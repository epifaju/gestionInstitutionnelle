package com.app.modules.rh.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.notifications.service.NotificationService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    @Mock private NotificationService notificationService;

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
    void testCalendrier_EMPLOYE_VoitSeulementSesConges() {
        // switch to EMPLOYE context
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setEmail("emp@test.com");
        u.setRole(Role.EMPLOYE);
        Organisation o = new Organisation();
        o.setId(orgId);
        u.setOrganisation(o);
        CustomUserDetails ud = new CustomUserDetails(u);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));

        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.of(s));

        CongeAbsence c = new CongeAbsence();
        c.setId(UUID.fromString("d2000000-0000-0000-0000-000000000001"));
        c.setOrganisationId(orgId);
        c.setSalarie(s);
        c.setTypeConge(TypeConge.ANNUEL);
        c.setDateDebut(LocalDate.of(2026, 4, 22));
        c.setDateFin(LocalDate.of(2026, 4, 30));
        c.setNbJours(new BigDecimal("7"));
        c.setStatut(StatutConge.VALIDE);

        when(congeRepository.findCalendrierSalarie(orgId, salarieId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .thenReturn(List.of(c));

        var res = congeService.getCalendrier(orgId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertThat(res).hasSize(1);
        assertThat(res.getFirst().salarieId()).isEqualTo(salarieId);
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
    void getDroits_absent_creeDroitsSelonEmbauche() {
        Salarie sal = new Salarie();
        sal.setId(salarieId);
        sal.setOrganisationId(orgId);
        sal.setDateEmbauche(LocalDate.of(2026, 4, 30));
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(sal));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, 2026)).thenReturn(Optional.empty());
        when(droitsCongesRepository.save(any(DroitsConges.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = congeService.getDroits(salarieId, 2026, orgId);

        // Avr→Déc = 9 mois × 2,5 = 22,5
        assertThat(dto.annee()).isEqualTo(2026);
        assertThat(dto.joursDroit()).isEqualByComparingTo("22.5");
        assertThat(dto.joursPris()).isEqualByComparingTo("0.0");
        assertThat(dto.joursRestants()).isEqualByComparingTo("22.5");
        verify(droitsCongesRepository).save(any(DroitsConges.class));
    }

    @Test
    void getDroits_existant_neRecreePas() {
        Salarie sal = new Salarie();
        sal.setId(salarieId);
        sal.setOrganisationId(orgId);
        sal.setDateEmbauche(LocalDate.of(2020, 1, 1));
        DroitsConges existing = new DroitsConges();
        existing.setAnnee(2026);
        existing.setJoursDroit(new BigDecimal("30.0"));
        existing.setJoursPris(new BigDecimal("5.0"));
        existing.setJoursRestants(new BigDecimal("25.0"));
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(sal));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, 2026)).thenReturn(Optional.of(existing));

        var dto = congeService.getDroits(salarieId, 2026, orgId);

        assertThat(dto.joursRestants()).isEqualByComparingTo("25.0");
        verify(droitsCongesRepository, never()).save(any());
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

    @Test
    void listConges_employeSansSalarie_pageVide() {
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setEmail("emp@test.com");
        u.setRole(Role.EMPLOYE);
        Organisation o = new Organisation();
        o.setId(orgId);
        u.setOrganisation(o);
        CustomUserDetails ud = new CustomUserDetails(u);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));

        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.empty());
        when(utilisateurRepository.findById(userId)).thenReturn(Optional.of(u));
        when(salarieRepository.findByOrganisationIdAndEmailIgnoreCase(orgId, "emp@test.com")).thenReturn(Optional.empty());

        var page = congeService.listConges(orgId, null, null, null, null, null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void listConges_employeAutreSalarie_refuse() {
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setEmail("emp@test.com");
        u.setRole(Role.EMPLOYE);
        Organisation o = new Organisation();
        o.setId(orgId);
        u.setOrganisation(o);
        CustomUserDetails ud = new CustomUserDetails(u);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));

        Salarie own = new Salarie();
        own.setId(salarieId);
        own.setOrganisationId(orgId);
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.of(own));

        assertThatThrownBy(() ->
                        congeService.listConges(
                                orgId, null, null, null, null, null, UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONGE_NON_AUTORISE");
    }

    @Test
    void listConges_rh_ok() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("Doe");
        s.setPrenom("Jane");
        s.setService("RH");
        CongeAbsence c = new CongeAbsence();
        c.setId(UUID.fromString("d3000000-0000-0000-0000-000000000001"));
        c.setOrganisationId(orgId);
        c.setSalarie(s);
        c.setTypeConge(TypeConge.ANNUEL);
        c.setDateDebut(LocalDate.of(2026, 5, 1));
        c.setDateFin(LocalDate.of(2026, 5, 5));
        c.setNbJours(new BigDecimal("5"));
        c.setStatut(StatutConge.EN_ATTENTE);

        when(congeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1));

        var page = congeService.listConges(orgId, "EN_ATTENTE", null, null, null, null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).statut()).isEqualTo("EN_ATTENTE");
    }

    @Test
    void getById_orgMismatch() {
        CongeAbsence c = new CongeAbsence();
        c.setId(UUID.fromString("d4000000-0000-0000-0000-000000000001"));
        c.setOrganisationId(UUID.randomUUID());
        when(congeRepository.findById(c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> congeService.getById(c.getId(), orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONGE_ORG_MISMATCH");
    }

    @Test
    void listMesConges_nonLie() {
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> congeService.listMesConges(orgId, userId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_NON_LIE");
    }

    @Test
    void soumettre_brouillon_sansControleSolde() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("Doe");
        s.setPrenom("Jane");
        s.setService("RH");
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));
        when(congeRepository.save(any(CongeAbsence.class))).thenAnswer(inv -> {
            CongeAbsence c = inv.getArgument(0);
            c.setId(UUID.fromString("d5000000-0000-0000-0000-000000000001"));
            return c;
        });

        CongeRequest req =
                new CongeRequest(salarieId, "ANNUEL", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), null);
        congeService.soumettre(req, true, orgId, userId);

        ArgumentCaptor<CongeAbsence> cap = ArgumentCaptor.forClass(CongeAbsence.class);
        verify(congeRepository).save(cap.capture());
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutConge.BROUILLON);
        verify(congeRepository, never()).existsChevauchement(any(), any(), any(), any());
    }

    @Test
    void soumettreBrouillon_ok() {
        UUID congeId = UUID.fromString("d6000000-0000-0000-0000-000000000001");
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("Doe");
        s.setPrenom("Jane");
        s.setService("RH");

        CongeAbsence c = new CongeAbsence();
        c.setId(congeId);
        c.setOrganisationId(orgId);
        c.setSalarie(s);
        c.setTypeConge(TypeConge.ANNUEL);
        c.setDateDebut(LocalDate.of(2026, 8, 1));
        c.setDateFin(LocalDate.of(2026, 8, 5));
        c.setNbJours(new BigDecimal("5"));
        c.setStatut(StatutConge.BROUILLON);
        when(congeRepository.findById(congeId)).thenReturn(Optional.of(c));

        DroitsConges d = new DroitsConges();
        d.setJoursRestants(new BigDecimal("20"));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, 2026)).thenReturn(Optional.of(d));
        when(congeRepository.existsChevauchement(salarieId, c.getDateDebut(), c.getDateFin(), StatutConge.VALIDE))
                .thenReturn(false);
        when(congeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRepository.findByOrganisationIdAndRoleAndActifTrue(orgId, Role.RH)).thenReturn(List.of());

        var out = congeService.soumettreBrouillon(congeId, orgId, userId);
        assertThat(out.statut()).isEqualTo("EN_ATTENTE");
    }

    @Test
    void getMesDroitsConges_ok() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setDateEmbauche(LocalDate.of(2020, 1, 1));
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.of(s));

        DroitsConges d = new DroitsConges();
        d.setAnnee(LocalDate.now().getYear());
        d.setJoursDroit(new BigDecimal("30"));
        d.setJoursPris(new BigDecimal("2"));
        d.setJoursRestants(new BigDecimal("28"));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, LocalDate.now().getYear()))
                .thenReturn(Optional.of(d));

        var dto = congeService.getMesDroitsConges(orgId, userId);
        assertThat(dto.joursRestants()).isEqualByComparingTo("28");
    }
}
