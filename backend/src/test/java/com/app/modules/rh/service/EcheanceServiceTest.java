package com.app.modules.rh.service;

import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.rh.dto.EcheanceRequest;
import com.app.modules.rh.dto.TraiterEcheanceRequest;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.TypeEcheance;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcheanceServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SALARIE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID ECHEANCE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    @Mock private EcheanceRhRepository echeanceRepository;
    @Mock private SalarieRepository salarieRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private MinioStorageService minioStorageService;

    @InjectMocks private EcheanceService echeanceService;

    private Salarie salarie() {
        Salarie s = new Salarie();
        s.setId(SALARIE_ID);
        s.setOrganisationId(ORG_ID);
        s.setNom("Dupont");
        s.setPrenom("Alice");
        s.setService("IT");
        s.setMatricule("M1");
        return s;
    }

    @Test
    void creerEcheance_refuseSiDateNonFuture() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        EcheanceRequest req = new EcheanceRequest(
                SALARIE_ID, null, "FIN_CDD", "Test", null, LocalDate.now(), 2, null);

        assertThatThrownBy(() -> echeanceService.creerEcheance(req, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ECHEANCE_DATE_INVALIDE");
        verify(echeanceRepository, never()).save(any());
    }

    @Test
    void creerEcheance_refuseSiDoublonActif() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        when(echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                        eq(ORG_ID), eq(SALARIE_ID), any(), any(), any()))
                .thenReturn(true);
        EcheanceRequest req = new EcheanceRequest(
                SALARIE_ID, null, "FIN_CDD", "Test", null, LocalDate.now().plusDays(10), 2, null);

        assertThatThrownBy(() -> echeanceService.creerEcheance(req, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ECHEANCE_DEJA_EXISTANTE");
    }

    @Test
    void creerEcheance_ok_actionRequiseSous7Jours() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        when(echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                        eq(ORG_ID), eq(SALARIE_ID), any(), any(), any()))
                .thenReturn(false);
        when(echeanceRepository.save(any(EcheanceRh.class))).thenAnswer(inv -> {
            EcheanceRh e = inv.getArgument(0);
            e.setId(ECHEANCE_ID);
            return e;
        });

        var out = echeanceService.creerEcheance(
                new EcheanceRequest(SALARIE_ID, null, "FIN_CDD", "Fin CDD", "desc",
                        LocalDate.now().plusDays(3), null, null),
                ORG_ID,
                USER_ID);
        assertThat(out.statut()).isEqualTo("ACTION_REQUISE");
        assertThat(out.niveauUrgence()).isEqualTo("URGENT");
    }

    @Test
    void creerEcheance_ok_enAlerteEtAVenir() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        when(echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                        eq(ORG_ID), eq(SALARIE_ID), any(), any(), any()))
                .thenReturn(false);
        when(echeanceRepository.save(any(EcheanceRh.class))).thenAnswer(inv -> {
            EcheanceRh e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        var alerte = echeanceService.creerEcheance(
                new EcheanceRequest(SALARIE_ID, null, "VISITE_MEDICALE", "Visite", null,
                        LocalDate.now().plusDays(15), 1, null),
                ORG_ID, USER_ID);
        assertThat(alerte.statut()).isEqualTo("EN_ALERTE");
        assertThat(alerte.niveauUrgence()).isEqualTo("ATTENTION");

        var aVenir = echeanceService.creerEcheance(
                new EcheanceRequest(SALARIE_ID, null, "TITRE_SEJOUR", "Titre", null,
                        LocalDate.now().plusDays(60), 2, null),
                ORG_ID, USER_ID);
        assertThat(aVenir.statut()).isEqualTo("A_VENIR");
        assertThat(aVenir.niveauUrgence()).isEqualTo("NORMAL");
    }

    @Test
    void traiterEcheance_ok() {
        EcheanceRh e = new EcheanceRh();
        e.setId(ECHEANCE_ID);
        e.setOrganisationId(ORG_ID);
        e.setSalarie(salarie());
        e.setTypeEcheance(TypeEcheance.FIN_CDD);
        e.setTitre("Fin");
        e.setDateEcheance(LocalDate.now().plusDays(5));
        e.setStatut(StatutEcheance.ACTION_REQUISE);
        when(echeanceRepository.findByIdAndOrganisationId(ECHEANCE_ID, ORG_ID)).thenReturn(Optional.of(e));
        when(echeanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRepository.findById(USER_ID)).thenReturn(Optional.of(utilisateur()));

        var out = echeanceService.traiterEcheance(
                ECHEANCE_ID,
                new TraiterEcheanceRequest(LocalDate.now(), "fait"),
                null,
                ORG_ID,
                USER_ID);
        assertThat(out.statut()).isEqualTo("TRAITEE");
        assertThat(out.commentaireTraitement()).isEqualTo("fait");
    }

    @Test
    void traiterEcheance_refuseDejaTraitee() {
        EcheanceRh e = new EcheanceRh();
        e.setId(ECHEANCE_ID);
        e.setOrganisationId(ORG_ID);
        e.setStatut(StatutEcheance.TRAITEE);
        when(echeanceRepository.findByIdAndOrganisationId(ECHEANCE_ID, ORG_ID)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> echeanceService.traiterEcheance(
                        ECHEANCE_ID, new TraiterEcheanceRequest(LocalDate.now(), null), null, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONTRAT_STATUT_INVALIDE");
    }

    @Test
    void annulerEcheance_ok() {
        EcheanceRh e = new EcheanceRh();
        e.setId(ECHEANCE_ID);
        e.setOrganisationId(ORG_ID);
        e.setSalarie(salarie());
        e.setTypeEcheance(TypeEcheance.AUTRE);
        e.setTitre("X");
        e.setDateEcheance(LocalDate.now().minusDays(2));
        e.setStatut(StatutEcheance.A_VENIR);
        when(echeanceRepository.findByIdAndOrganisationId(ECHEANCE_ID, ORG_ID)).thenReturn(Optional.of(e));
        when(echeanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var out = echeanceService.annulerEcheance(ECHEANCE_ID, "annulé", ORG_ID);
        assertThat(out.statut()).isEqualTo("ANNULEE");
        assertThat(out.niveauUrgence()).isEqualTo("CRITIQUE");
    }

    @Test
    void getDashboard_ok() {
        when(echeanceRepository.countByOrganisationIdAndStatutIn(eq(ORG_ID), any())).thenReturn(5L);
        when(echeanceRepository.countExpireesByOrganisationId(eq(ORG_ID), any())).thenReturn(1L);
        when(echeanceRepository.countActivesBetween(eq(ORG_ID), any(), any())).thenReturn(2L);
        when(echeanceRepository.countByOrganisationIdAndTypeEcheanceAndStatutNotIn(eq(ORG_ID), any(), any()))
                .thenReturn(1L);
        when(echeanceRepository.findTopUrgentes(eq(ORG_ID), any())).thenReturn(List.of());

        var dash = echeanceService.getDashboard(ORG_ID);
        assertThat(dash.totalActives()).isEqualTo(5L);
        assertThat(dash.critiques()).isEqualTo(1L);
        assertThat(dash.prochainesEcheances()).isEmpty();
    }

    @Test
    void creerEcheance_refuseTypeInvalide() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        assertThatThrownBy(() -> echeanceService.creerEcheance(
                        new EcheanceRequest(SALARIE_ID, null, "PAS_UN_TYPE", "T", null,
                                LocalDate.now().plusDays(5), 2, null),
                        ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ECHEANCE_DATE_INVALIDE");
    }

    @Test
    void listEcheances_filtresInvalides_ignores() {
        when(echeanceRepository.findByOrganisationIdAndFilters(
                        eq(ORG_ID), eq(null), eq(null), eq(null), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        var page = echeanceService.listEcheances(
                ORG_ID, "NOT_A_STATUT", "NOT_A_TYPE", null, null, null, PageRequest.of(0, 5));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void listEcheances_filtresBlank_traitesCommeNull() {
        when(echeanceRepository.findByOrganisationIdAndFilters(
                        eq(ORG_ID), eq(null), eq(null), eq(null), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        var page = echeanceService.listEcheances(
                ORG_ID, "  ", "", null, null, null, PageRequest.of(0, 5));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void listEcheances_ok() {
        EcheanceRh e = new EcheanceRh();
        e.setId(ECHEANCE_ID);
        e.setOrganisationId(ORG_ID);
        e.setSalarie(salarie());
        e.setTypeEcheance(TypeEcheance.FIN_CDD);
        e.setTitre("T");
        e.setDateEcheance(LocalDate.now().plusDays(20));
        e.setStatut(StatutEcheance.EN_ALERTE);
        when(echeanceRepository.findByOrganisationIdAndFilters(
                        eq(ORG_ID), eq(StatutEcheance.EN_ALERTE), eq(TypeEcheance.FIN_CDD),
                        eq(SALARIE_ID), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(e)));

        var page = echeanceService.listEcheances(
                ORG_ID, "EN_ALERTE", "FIN_CDD", SALARIE_ID, null, null, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().niveauUrgence()).isEqualTo("ATTENTION");
    }

    private Utilisateur utilisateur() {
        Utilisateur u = new Utilisateur();
        u.setId(USER_ID);
        u.setNom("Admin");
        u.setPrenom("User");
        u.setEmail("a@test.com");
        return u;
    }
}
