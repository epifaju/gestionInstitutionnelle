package com.app.modules.rh.service;

import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.notifications.service.NotificationService;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rh.dto.ContratRequest;
import com.app.modules.rh.dto.RenouvellementCddRequest;
import com.app.modules.rh.dto.TraiterEcheanceRequest;
import com.app.modules.rh.entity.ContratSalarie;
import com.app.modules.rh.entity.ConfigAlerteRh;
import com.app.modules.rh.entity.DecisionFinCdd;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.TypeEcheance;
import com.app.modules.rh.repository.ConfigAlerteRhRepository;
import com.app.modules.rh.repository.ContratSalarieRepository;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.FormationObligatoireRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContratServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID salarieId = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID contratId = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    @Mock
    private ContratSalarieRepository contratRepository;
    @Mock
    private EcheanceRhRepository echeanceRepository;
    @Mock
    private SalarieRepository salarieRepository;
    @Mock
    private ConfigAlerteRhRepository configRepository;
    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private ContratService contratService;

    private Salarie salarie() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("Dupont");
        s.setPrenom("Jean");
        s.setMatricule("M001");
        s.setService("IT");
        return s;
    }

    private void stubSaveContratAssignsId() {
        when(contratRepository.save(any(ContratSalarie.class))).thenAnswer(inv -> {
            ContratSalarie c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(contratId);
            }
            return c;
        });
    }

    @Test
    void testCreerContratCdd_Ok() {
        LocalDate today = LocalDate.now();
        LocalDate dateFin = today.plusDays(90);

        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.of(salarie()));
        when(contratRepository.findBySalarieIdAndActifTrue(salarieId)).thenReturn(Optional.empty());
        stubSaveContratAssignsId();
        when(echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                eq(orgId), eq(salarieId), eq(TypeEcheance.FIN_CDD), eq(dateFin), any()))
                .thenReturn(false);

        ContratRequest req = new ContratRequest(
                "CDD",
                today.minusDays(10),
                dateFin,
                null,
                null,
                null,
                "Développeur",
                "Remplacement",
                null);

        contratService.creerContrat(req, salarieId, orgId, userId);

        ArgumentCaptor<ContratSalarie> contratCap = ArgumentCaptor.forClass(ContratSalarie.class);
        verify(contratRepository).save(contratCap.capture());
        assertThat(contratCap.getValue().isActif()).isTrue();
        assertThat(contratCap.getValue().getTypeContrat()).isEqualTo("CDD");

        ArgumentCaptor<EcheanceRh> echeanceCap = ArgumentCaptor.forClass(EcheanceRh.class);
        verify(echeanceRepository).save(echeanceCap.capture());
        EcheanceRh e = echeanceCap.getValue();
        assertThat(e.getTypeEcheance()).isEqualTo(TypeEcheance.FIN_CDD);
        assertThat(e.getDateEcheance()).isEqualTo(dateFin);
        assertThat(e.getStatut()).isEqualTo(StatutEcheance.A_VENIR);
        assertThat(e.getContratId()).isEqualTo(contratId);
    }

    @Test
    void testCreerContratCdd_SansMotif_LanceException() {
        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.of(salarie()));

        ContratRequest req = new ContratRequest(
                "CDD",
                LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(60),
                null,
                null,
                null,
                "Poste",
                "   ",
                null);

        assertThatThrownBy(() -> contratService.creerContrat(req, salarieId, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_REQUIS");

        verify(contratRepository, never()).save(any());
    }

    @Test
    void testRenouvelerCdd_Ok() {
        UUID ancienContratId = UUID.fromString("e0000000-0000-0000-0000-000000000001");
        UUID nouveauContratId = UUID.fromString("f0000000-0000-0000-0000-000000000001");
        LocalDate nouvelleFin = LocalDate.now().plusDays(120);

        Salarie s = salarie();
        ContratSalarie ancien = new ContratSalarie();
        ancien.setId(ancienContratId);
        ancien.setOrganisationId(orgId);
        ancien.setSalarie(s);
        ancien.setTypeContrat("CDD");
        ancien.setDateDebutContrat(LocalDate.of(2024, 1, 1));
        ancien.setDateFinContrat(LocalDate.of(2024, 12, 31));
        ancien.setRenouvellementNumero(0);
        ancien.setActif(true);
        ancien.setDecisionFin(DecisionFinCdd.EN_ATTENTE);

        EcheanceRh oldE = new EcheanceRh();
        oldE.setId(UUID.randomUUID());
        oldE.setOrganisationId(orgId);
        oldE.setSalarie(s);
        oldE.setContratId(ancienContratId);
        oldE.setTypeEcheance(TypeEcheance.FIN_CDD);
        oldE.setStatut(StatutEcheance.A_VENIR);

        ConfigAlerteRh cfg = new ConfigAlerteRh();
        cfg.setMaxRenouvellementsCdd(2);

        when(contratRepository.findByIdAndOrganisationId(ancienContratId, orgId)).thenReturn(Optional.of(ancien));
        when(configRepository.findByOrganisationId(orgId)).thenReturn(Optional.of(cfg));
        when(echeanceRepository.findByOrganisationIdAndContratIdAndType(orgId, ancienContratId, TypeEcheance.FIN_CDD))
                .thenReturn(Optional.of(oldE));
        when(echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                eq(orgId), eq(salarieId), eq(TypeEcheance.FIN_CDD), eq(nouvelleFin), any()))
                .thenReturn(false);

        when(contratRepository.save(any(ContratSalarie.class))).thenAnswer(inv -> {
            ContratSalarie c = inv.getArgument(0);
            if (c.getId() == null && c.isActif()) {
                c.setId(nouveauContratId);
            }
            return c;
        });

        contratService.renouvelerCdd(
                ancienContratId,
                new RenouvellementCddRequest(nouvelleFin, "Prolongation", null),
                orgId,
                userId);

        assertThat(ancien.isActif()).isFalse();

        ArgumentCaptor<ContratSalarie> contratCap = ArgumentCaptor.forClass(ContratSalarie.class);
        verify(contratRepository, times(2)).save(contratCap.capture());
        List<ContratSalarie> saved = contratCap.getAllValues();
        ContratSalarie nouveau = saved.stream().filter(ContratSalarie::isActif).findFirst().orElseThrow();
        assertThat(nouveau.getRenouvellementNumero()).isEqualTo(1);
        assertThat(nouveau.getContratParentId()).isEqualTo(ancienContratId);

        assertThat(oldE.getStatut()).isEqualTo(StatutEcheance.ANNULEE);

        ArgumentCaptor<EcheanceRh> echCap = ArgumentCaptor.forClass(EcheanceRh.class);
        verify(echeanceRepository, atLeast(2)).save(echCap.capture());
        EcheanceRh newE = echCap.getAllValues().stream()
                .filter(x -> x.getTypeEcheance() == TypeEcheance.FIN_CDD && x.getStatut() != StatutEcheance.ANNULEE)
                .reduce((a, b) -> b)
                .orElseThrow();
        assertThat(newE.getContratId()).isEqualTo(nouveauContratId);
        assertThat(newE.getDateEcheance()).isEqualTo(nouvelleFin);
    }

    @Test
    void testRenouvelerCdd_MaxAtteint_LanceException() {
        UUID ancienContratId = UUID.fromString("e0000000-0000-0000-0000-000000000001");
        Salarie s = salarie();
        ContratSalarie ancien = new ContratSalarie();
        ancien.setId(ancienContratId);
        ancien.setOrganisationId(orgId);
        ancien.setSalarie(s);
        ancien.setTypeContrat("CDD");
        ancien.setDateDebutContrat(LocalDate.of(2024, 1, 1));
        ancien.setRenouvellementNumero(2);
        ancien.setActif(true);
        ancien.setDecisionFin(DecisionFinCdd.EN_ATTENTE);

        ConfigAlerteRh cfg = new ConfigAlerteRh();
        cfg.setMaxRenouvellementsCdd(2);

        when(contratRepository.findByIdAndOrganisationId(ancienContratId, orgId)).thenReturn(Optional.of(ancien));
        when(configRepository.findByOrganisationId(orgId)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() -> contratService.renouvelerCdd(
                ancienContratId,
                new RenouvellementCddRequest(LocalDate.now().plusDays(200), "x", null),
                orgId,
                userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ECHEANCE_RENOUVELLEMENT_IMPOSSIBLE");

        verify(contratRepository, never()).save(any());
    }

    @Test
    void testCalculStatutInitial_EcheanceDans5Jours() {
        LocalDate today = LocalDate.now();
        LocalDate dateFin = today.plusDays(5);

        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.of(salarie()));
        when(contratRepository.findBySalarieIdAndActifTrue(salarieId)).thenReturn(Optional.empty());
        stubSaveContratAssignsId();
        when(echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                any(), any(), any(), eq(dateFin), any())).thenReturn(false);

        ContratRequest req = new ContratRequest(
                "CDD",
                today.minusDays(30),
                dateFin,
                null,
                null,
                null,
                "Poste",
                "Accroissement",
                null);

        contratService.creerContrat(req, salarieId, orgId, userId);

        ArgumentCaptor<EcheanceRh> cap = ArgumentCaptor.forClass(EcheanceRh.class);
        verify(echeanceRepository).save(cap.capture());
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutEcheance.ACTION_REQUISE);
    }

    @Test
    void testCalculStatutInitial_EcheanceDans20Jours() {
        LocalDate today = LocalDate.now();
        LocalDate dateFin = today.plusDays(20);

        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.of(salarie()));
        when(contratRepository.findBySalarieIdAndActifTrue(salarieId)).thenReturn(Optional.empty());
        stubSaveContratAssignsId();
        when(echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                any(), any(), any(), eq(dateFin), any())).thenReturn(false);

        ContratRequest req = new ContratRequest(
                "CDD",
                today.minusDays(10),
                dateFin,
                null,
                null,
                null,
                "Poste",
                "Remplacement",
                null);

        contratService.creerContrat(req, salarieId, orgId, userId);

        ArgumentCaptor<EcheanceRh> cap = ArgumentCaptor.forClass(EcheanceRh.class);
        verify(echeanceRepository).save(cap.capture());
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutEcheance.EN_ALERTE);
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class TraiterEcheanceNested {

        @Mock
        private EcheanceRhRepository echeanceRepositoryNested;
        @Mock
        private SalarieRepository salarieRepositoryNested;
        @Mock
        private com.app.modules.auth.repository.UtilisateurRepository utilisateurRepository;
        @Mock
        private MinioStorageService minioNested;

        @InjectMocks
        private EcheanceService echeanceService;

        @Test
        void testTraiterEcheance_StatutMisAJour() {
            UUID echeanceId = UUID.fromString("10000000-0000-0000-0000-000000000001");
            LocalDate dateTrait = LocalDate.now();

            Salarie s = salarie();
            EcheanceRh e = new EcheanceRh();
            e.setId(echeanceId);
            e.setOrganisationId(orgId);
            e.setSalarie(s);
            e.setTypeEcheance(TypeEcheance.FIN_CDD);
            e.setDateEcheance(LocalDate.now().plusDays(1));
            e.setStatut(StatutEcheance.ACTION_REQUISE);
            e.setTitre("Test");

            when(echeanceRepositoryNested.findByIdAndOrganisationId(echeanceId, orgId)).thenReturn(Optional.of(e));

            echeanceService.traiterEcheance(
                    echeanceId,
                    new TraiterEcheanceRequest(dateTrait, "OK"),
                    null,
                    orgId,
                    userId);

            assertThat(e.getStatut()).isEqualTo(StatutEcheance.TRAITEE);
            assertThat(e.getDateTraitement()).isEqualTo(dateTrait);
            assertThat(e.getTraitePar()).isEqualTo(userId);
            verify(echeanceRepositoryNested).save(e);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SchedulerNested {

        @Mock
        private EcheanceRhRepository echeanceRepositorySch;
        @Mock
        private FormationObligatoireRepository formationRepository;
        @Mock
        private ContratSalarieRepository contratRepositorySch;
        @Mock
        private OrganisationRepository organisationRepository;
        @Mock
        private NotificationService notificationService;

        @InjectMocks
        private EcheanceScheduler scheduler;

        @Test
        void testScheduler_MarqueEcheancesExpirees() {
            LocalDate today = LocalDate.now();
            Salarie s1 = salarie();
            s1.setNom("A");
            s1.setPrenom("Un");
            Salarie s2 = salarie();
            s2.setId(UUID.randomUUID());
            s2.setNom("B");
            s2.setPrenom("Deux");

            EcheanceRh e1 = new EcheanceRh();
            e1.setId(UUID.randomUUID());
            e1.setOrganisationId(orgId);
            e1.setSalarie(s1);
            e1.setTitre("E1");
            e1.setTypeEcheance(TypeEcheance.FIN_CDD);
            e1.setDateEcheance(today.minusDays(1));
            e1.setStatut(StatutEcheance.EN_ALERTE);
            e1.setRappelJ0Envoye(false);

            EcheanceRh e2 = new EcheanceRh();
            e2.setId(UUID.randomUUID());
            e2.setOrganisationId(orgId);
            e2.setSalarie(s2);
            e2.setTitre("E2");
            e2.setTypeEcheance(TypeEcheance.VISITE_MEDICALE);
            e2.setDateEcheance(today.minusDays(5));
            e2.setStatut(StatutEcheance.ACTION_REQUISE);
            e2.setRappelJ0Envoye(false);

            when(echeanceRepositorySch.findEcheancesAlerteJ30(today)).thenReturn(List.of());
            when(echeanceRepositorySch.findEcheancesAlerteJ7(today)).thenReturn(List.of());
            when(echeanceRepositorySch.findEcheancesExpirees(today)).thenReturn(List.of(e1, e2));
            when(formationRepository.findToutesExpireesAvant(today)).thenReturn(List.of());

            scheduler.verifierEcheancesQuotidien();

            assertThat(e1.getStatut()).isEqualTo(StatutEcheance.EXPIREE);
            assertThat(e2.getStatut()).isEqualTo(StatutEcheance.EXPIREE);
            assertThat(e1.isRappelJ0Envoye()).isTrue();
            assertThat(e2.isRappelJ0Envoye()).isTrue();

            verify(notificationService, times(2)).envoyer(
                    eq(orgId),
                    eq(null),
                    any(NotificationType.class),
                    any(),
                    any(),
                    any());
            verify(echeanceRepositorySch, times(2)).save(any(EcheanceRh.class));
        }
    }
}
