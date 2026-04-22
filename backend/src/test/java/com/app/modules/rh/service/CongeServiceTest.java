package com.app.modules.rh.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.CongeRequest;
import com.app.modules.rh.dto.CongeValidationRequest;
import com.app.modules.rh.entity.CongeAbsence;
import com.app.modules.rh.entity.DroitsConges;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutConge;
import com.app.modules.rh.entity.TypeConge;
import com.app.modules.rh.repository.CongeRepository;
import com.app.modules.rh.repository.DroitsCongesRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CongeServiceTest {

    @Mock private CongeRepository congeRepository;
    @Mock private SalarieRepository salarieRepository;
    @Mock private DroitsCongesRepository droitsCongesRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private CongeService congeService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private final UUID salarieId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setSecurityContextRh() {
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setEmail("rh@test.com");
        u.setRole(Role.RH);
        Organisation o = new Organisation();
        o.setId(orgId);
        u.setOrganisation(o);
        CustomUserDetails ud = new CustomUserDetails(u);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testSoumettreConge_OK() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("Doe");
        s.setPrenom("Jane");
        s.setService("RH");
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));

        DroitsConges d = new DroitsConges();
        d.setJoursRestants(new BigDecimal("25"));
        d.setJoursPris(BigDecimal.ZERO);
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, 2025)).thenReturn(Optional.of(d));
        when(congeRepository.existsChevauchement(salarieId, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 5), StatutConge.VALIDE))
                .thenReturn(false);

        CongeRequest req =
                new CongeRequest(salarieId, "ANNUEL", LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 5), null);
        when(congeRepository.save(any(CongeAbsence.class)))
                .thenAnswer(
                        inv -> {
                            CongeAbsence c = inv.getArgument(0);
                            c.setId(UUID.fromString("d0000000-0000-0000-0000-000000000001"));
                            return c;
                        });

        congeService.soumettre(req, false, orgId, userId);

        ArgumentCaptor<CongeAbsence> cap = ArgumentCaptor.forClass(CongeAbsence.class);
        verify(congeRepository).save(cap.capture());
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutConge.EN_ATTENTE);
        assertThat(cap.getValue().getTypeConge()).isEqualTo(TypeConge.ANNUEL);
    }

    @Test
    void testSoumettreConge_ChevauchementRejete() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("Doe");
        s.setPrenom("Jane");
        s.setService("RH");
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));

        DroitsConges d = new DroitsConges();
        d.setJoursRestants(new BigDecimal("30"));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, 2025)).thenReturn(Optional.of(d));
        when(congeRepository.existsChevauchement(salarieId, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 3), StatutConge.VALIDE))
                .thenReturn(true);

        CongeRequest req =
                new CongeRequest(salarieId, "ANNUEL", LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 3), null);

        assertThatThrownBy(() -> congeService.soumettre(req, false, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONGE_CHEVAUCHEMENT");
    }

    @Test
    void testSoumettreConge_SoldeInsuffisant() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("Doe");
        s.setPrenom("Jane");
        s.setService("RH");
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));

        DroitsConges d = new DroitsConges();
        d.setJoursRestants(new BigDecimal("1"));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, 2025)).thenReturn(Optional.of(d));

        CongeRequest req =
                new CongeRequest(salarieId, "ANNUEL", LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 10), null);

        assertThatThrownBy(() -> congeService.soumettre(req, false, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONGE_SOLDE_INSUFFISANT");
    }

    @Test
    void testValiderConge_DecrementeJours() {
        UUID congeId = UUID.fromString("e0000000-0000-0000-0000-000000000001");
        Salarie sal = new Salarie();
        sal.setId(salarieId);
        sal.setOrganisationId(orgId);
        sal.setNom("Doe");
        sal.setPrenom("John");
        sal.setService("RH");

        CongeAbsence c = new CongeAbsence();
        c.setId(congeId);
        c.setOrganisationId(orgId);
        c.setSalarie(sal);
        c.setTypeConge(TypeConge.ANNUEL);
        c.setDateDebut(LocalDate.of(2025, 8, 1));
        c.setDateFin(LocalDate.of(2025, 8, 5));
        c.setNbJours(new BigDecimal("5"));
        c.setStatut(StatutConge.EN_ATTENTE);
        when(congeRepository.findById(congeId)).thenReturn(Optional.of(c));

        DroitsConges droits = new DroitsConges();
        droits.setJoursPris(BigDecimal.ZERO);
        droits.setJoursRestants(new BigDecimal("20"));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, 2025)).thenReturn(Optional.of(droits));

        Utilisateur val = new Utilisateur();
        val.setId(userId);
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(val);

        congeService.valider(congeId, userId, orgId);

        ArgumentCaptor<DroitsConges> dcap = ArgumentCaptor.forClass(DroitsConges.class);
        verify(droitsCongesRepository).save(dcap.capture());
        assertThat(dcap.getValue().getJoursRestants()).isEqualByComparingTo(new BigDecimal("15"));
        assertThat(dcap.getValue().getJoursPris()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void testRejeterConge_EnAttente_NeTouchePasAuSolde() {
        UUID congeId = UUID.fromString("f0000000-0000-0000-0000-000000000001");
        Salarie sal = new Salarie();
        sal.setId(salarieId);
        sal.setOrganisationId(orgId);
        sal.setNom("Doe");
        sal.setPrenom("John");
        sal.setService("RH");

        CongeAbsence c = new CongeAbsence();
        c.setId(congeId);
        c.setOrganisationId(orgId);
        c.setSalarie(sal);
        c.setTypeConge(TypeConge.ANNUEL);
        c.setDateDebut(LocalDate.of(2025, 9, 1));
        c.setDateFin(LocalDate.of(2025, 9, 3));
        c.setNbJours(new BigDecimal("3"));
        c.setStatut(StatutConge.EN_ATTENTE);
        when(congeRepository.findById(congeId)).thenReturn(Optional.of(c));

        Utilisateur val = new Utilisateur();
        val.setId(userId);
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(val);

        congeService.rejeter(congeId, new CongeValidationRequest("erreur planning"), orgId);

        verify(droitsCongesRepository, never()).save(any());
        ArgumentCaptor<CongeAbsence> ccap = ArgumentCaptor.forClass(CongeAbsence.class);
        verify(congeRepository).save(ccap.capture());
        assertThat(ccap.getValue().getStatut()).isEqualTo(StatutConge.REJETE);
    }

    @Test
    void testAnnulerValide_RestaureJours() {
        UUID congeId = UUID.fromString("f1000000-0000-0000-0000-000000000001");
        Salarie sal = new Salarie();
        sal.setId(salarieId);
        sal.setOrganisationId(orgId);
        sal.setNom("Doe");
        sal.setPrenom("John");
        sal.setService("RH");

        CongeAbsence c = new CongeAbsence();
        c.setId(congeId);
        c.setOrganisationId(orgId);
        c.setSalarie(sal);
        c.setTypeConge(TypeConge.ANNUEL);
        c.setDateDebut(LocalDate.now().plusDays(14));
        c.setDateFin(LocalDate.now().plusDays(18));
        c.setNbJours(new BigDecimal("3"));
        c.setStatut(StatutConge.VALIDE);
        when(congeRepository.findById(congeId)).thenReturn(Optional.of(c));

        DroitsConges droits = new DroitsConges();
        droits.setJoursPris(new BigDecimal("3"));
        droits.setJoursRestants(new BigDecimal("17"));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, c.getDateDebut().getYear()))
                .thenReturn(Optional.of(droits));

        congeService.annulerValide(congeId, orgId);

        ArgumentCaptor<DroitsConges> dcap = ArgumentCaptor.forClass(DroitsConges.class);
        verify(droitsCongesRepository).save(dcap.capture());
        assertThat(dcap.getValue().getJoursPris()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dcap.getValue().getJoursRestants()).isEqualByComparingTo(new BigDecimal("20"));

        ArgumentCaptor<CongeAbsence> ccap = ArgumentCaptor.forClass(CongeAbsence.class);
        verify(congeRepository).save(ccap.capture());
        assertThat(ccap.getValue().getStatut()).isEqualTo(StatutConge.ANNULE);
    }

    @Test
    void testAnnulerValide_DateDebutPassee_Refuse() {
        UUID congeId = UUID.fromString("f2000000-0000-0000-0000-000000000001");
        Salarie sal = new Salarie();
        sal.setId(salarieId);
        sal.setOrganisationId(orgId);

        CongeAbsence c = new CongeAbsence();
        c.setId(congeId);
        c.setOrganisationId(orgId);
        c.setSalarie(sal);
        c.setTypeConge(TypeConge.ANNUEL);
        c.setDateDebut(LocalDate.now().minusDays(1));
        c.setDateFin(LocalDate.now().plusDays(3));
        c.setNbJours(BigDecimal.ONE);
        c.setStatut(StatutConge.VALIDE);
        when(congeRepository.findById(congeId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> congeService.annulerValide(congeId, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONGE_ANNULATION_DATE");
    }
}
