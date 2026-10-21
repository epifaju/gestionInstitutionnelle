package com.app.modules.rh.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.SalarieRequest;
import com.app.modules.rh.dto.SalarieResponse;
import com.app.modules.rh.entity.DroitsConges;
import com.app.modules.rh.entity.HistoriqueSalaire;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutSalarie;
import com.app.modules.rh.repository.DroitsCongesRepository;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalarieServiceTest {

    @Mock private SalarieRepository salarieRepository;
    @Mock private HistoriqueSalaireRepository historiqueSalaireRepository;
    @Mock private DroitsCongesRepository droitsCongesRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private MinioStorageService minioStorageService;

    @InjectMocks private SalarieService salarieService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private final UUID salarieId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setAuth() {
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
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private SalarieRequest req() {
        return new SalarieRequest(
                "Doe",
                "Jane",
                "jane@test.com",
                null,
                "Analyste",
                "RH",
                LocalDate.of(2024, 1, 15),
                "CDI",
                null,
                null,
                new BigDecimal("3000.00"),
                new BigDecimal("2300.00"),
                "EUR");
    }

    private Salarie owned(StatutSalarie statut) {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setMatricule("EMP-0001");
        s.setNom("Doe");
        s.setPrenom("Jane");
        s.setEmail("jane@test.com");
        s.setPoste("Analyste");
        s.setService("RH");
        s.setDateEmbauche(LocalDate.of(2024, 1, 15));
        s.setTypeContrat("CDI");
        s.setStatut(statut);
        s.setCreatedAt(Instant.parse("2024-01-15T10:00:00Z"));
        return s;
    }

    private HistoriqueSalaire grille(Salarie s) {
        HistoriqueSalaire h = new HistoriqueSalaire();
        h.setSalarie(s);
        h.setMontantBrut(new BigDecimal("3000.00"));
        h.setMontantNet(new BigDecimal("2300.00"));
        h.setDevise("EUR");
        h.setDateDebut(LocalDate.of(2024, 1, 15));
        h.setDateFin(null);
        return h;
    }

    private void stubToResponse(Salarie s, HistoriqueSalaire h) {
        when(historiqueSalaireRepository.findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(s.getId()))
                .thenReturn(Optional.ofNullable(h));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(eq(s.getId()), anyInt()))
                .thenReturn(Optional.empty());
    }

    @Test
    void getMe_SansLienEtSansEmail_TrouvePas() {
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salarieService.getMe(orgId, userId, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_ABSENT");
    }

    @Test
    void getMe_SansLien_EmailFallback_TrouvePas() {
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.empty());
        when(salarieRepository.findByOrganisationIdAndEmailIgnoreCase(orgId, "emp@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salarieService.getMe(orgId, userId, "emp@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_ABSENT");
    }

    @Test
    void getMe_ok_viaLienUtilisateur() {
        Salarie s = owned(StatutSalarie.ACTIF);
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.of(s));
        stubToResponse(s, grille(s));

        SalarieResponse out = salarieService.getMe(orgId, userId, null);

        assertThat(out.matricule()).isEqualTo("EMP-0001");
        assertThat(out.statut()).isEqualTo("ACTIF");
    }

    @Test
    void getMe_ok_viaEmailFallback() {
        Salarie s = owned(StatutSalarie.ACTIF);
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.empty());
        when(salarieRepository.findByOrganisationIdAndEmailIgnoreCase(orgId, "jane@test.com")).thenReturn(Optional.of(s));
        stubToResponse(s, grille(s));

        assertThat(salarieService.getMe(orgId, userId, "jane@test.com").email()).isEqualTo("jane@test.com");
    }

    @Test
    void getMeStrict_refuseSiNonLie() {
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salarieService.getMeStrict(orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_NON_LIE");
    }

    @Test
    void getById_refuseAutreOrg() {
        Salarie s = owned(StatutSalarie.ACTIF);
        s.setOrganisationId(UUID.randomUUID());
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> salarieService.getById(salarieId, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_ORG_MISMATCH");
    }

    @Test
    void listSalaries_ok() {
        Salarie s = owned(StatutSalarie.ACTIF);
        when(salarieRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(s), PageRequest.of(0, 20), 1));
        stubToResponse(s, grille(s));

        var page = salarieService.listSalaries(orgId, "ACTIF", "RH", "Doe", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).nom()).isEqualTo("Doe");
    }

    @Test
    void creer_ok_brouillonAvecGrilleEtDroits() {
        when(salarieRepository.countByOrganisationId(orgId)).thenReturn(0L);
        when(salarieRepository.save(any(Salarie.class))).thenAnswer(inv -> {
            Salarie s = inv.getArgument(0);
            s.setId(salarieId);
            s.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
            return s;
        });
        when(historiqueSalaireRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(droitsCongesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historiqueSalaireRepository.findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(salarieId))
                .thenAnswer(inv -> {
                    HistoriqueSalaire h = new HistoriqueSalaire();
                    h.setMontantBrut(new BigDecimal("3000.00"));
                    h.setMontantNet(new BigDecimal("2300.00"));
                    h.setDevise("EUR");
                    h.setDateDebut(LocalDate.of(2024, 1, 15));
                    return Optional.of(h);
                });
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(eq(salarieId), anyInt()))
                .thenReturn(Optional.empty());

        SalarieResponse out = salarieService.creer(req(), orgId);

        assertThat(out.matricule()).isEqualTo("EMP-0001");
        assertThat(out.statut()).isEqualTo("BROUILLON");
        verify(historiqueSalaireRepository).save(any(HistoriqueSalaire.class));
        verify(droitsCongesRepository).save(any(DroitsConges.class));
        verify(auditLogService).logRh(eq(orgId), eq(userId), eq("CREATE"), eq("Salarie"), eq(salarieId), any(), any());
    }

    @Test
    void modifier_refuseChangementSalaireDirect() {
        Salarie s = owned(StatutSalarie.ACTIF);
        HistoriqueSalaire h = grille(s);
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));
        when(historiqueSalaireRepository.findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(salarieId))
                .thenReturn(Optional.of(h));

        SalarieRequest changed = new SalarieRequest(
                "Doe", "Jane", "jane@test.com", null, "Analyste", "RH",
                LocalDate.of(2024, 1, 15), "CDI", null, null,
                new BigDecimal("3500.00"), new BigDecimal("2300.00"), "EUR");

        assertThatThrownBy(() -> salarieService.modifier(salarieId, changed, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALAIRE_MODIF_VIA_GRILLE");
    }

    @Test
    void modifier_ok_sansChangerSalaire() {
        Salarie s = owned(StatutSalarie.ACTIF);
        HistoriqueSalaire h = grille(s);
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));
        when(historiqueSalaireRepository.findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(salarieId))
                .thenReturn(Optional.of(h));
        when(salarieRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(eq(salarieId), anyInt()))
                .thenReturn(Optional.empty());

        SalarieRequest updated = new SalarieRequest(
                "Doe", "Jane", "jane@test.com", "0600000000", "Lead", "IT",
                LocalDate.of(2024, 1, 15), "CDI", null, null,
                new BigDecimal("3000.00"), new BigDecimal("2300.00"), "EUR");

        SalarieResponse out = salarieService.modifier(salarieId, updated, orgId);

        assertThat(out.poste()).isEqualTo("Lead");
        assertThat(out.service()).isEqualTo("IT");
    }

    @Test
    void validerDossier_ok() {
        Salarie s = owned(StatutSalarie.BROUILLON);
        HistoriqueSalaire h = grille(s);
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));
        when(historiqueSalaireRepository.findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(salarieId))
                .thenReturn(Optional.of(h));
        when(salarieRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(eq(salarieId), anyInt()))
                .thenReturn(Optional.empty());

        assertThat(salarieService.validerDossier(salarieId, orgId).statut()).isEqualTo("ACTIF");
    }

    @Test
    void validerDossier_refuseSiDejaActif() {
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(owned(StatutSalarie.ACTIF)));

        assertThatThrownBy(() -> salarieService.validerDossier(salarieId, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOSSIER_DEJA_VALIDE");
    }

    @Test
    void ajouterGrilleSalariale_ok() {
        Salarie s = owned(StatutSalarie.ACTIF);
        HistoriqueSalaire courant = grille(s);
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));
        when(historiqueSalaireRepository.findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(salarieId))
                .thenReturn(Optional.of(courant));
        when(historiqueSalaireRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(eq(salarieId), anyInt()))
                .thenReturn(Optional.empty());

        LocalDate debut = LocalDate.of(2025, 6, 1);
        salarieService.ajouterGrilleSalariale(
                salarieId, new BigDecimal("3200"), new BigDecimal("2450"), "EUR", debut, orgId);

        assertThat(courant.getDateFin()).isEqualTo(LocalDate.of(2025, 5, 31));
        ArgumentCaptor<HistoriqueSalaire> cap = ArgumentCaptor.forClass(HistoriqueSalaire.class);
        verify(historiqueSalaireRepository, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues().stream().anyMatch(h -> h.getDateFin() == null && h.getMontantBrut().compareTo(new BigDecimal("3200")) == 0))
                .isTrue();
    }

    @Test
    void ajouterGrilleSalariale_refuseDateInvalide() {
        Salarie s = owned(StatutSalarie.ACTIF);
        HistoriqueSalaire courant = grille(s);
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));
        when(historiqueSalaireRepository.findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(salarieId))
                .thenReturn(Optional.of(courant));

        assertThatThrownBy(() ->
                        salarieService.ajouterGrilleSalariale(
                                salarieId,
                                new BigDecimal("3200"),
                                new BigDecimal("2450"),
                                "EUR",
                                LocalDate.of(2024, 1, 15),
                                orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATE_GRILLE_INVALIDE");
    }

    @Test
    void uploadContrat_ok() throws Exception {
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(owned(StatutSalarie.ACTIF)));

        MockMultipartFile file =
                new MockMultipartFile("file", "contrat.pdf", "application/pdf", "%PDF-1.4\n".getBytes());

        String url = salarieService.uploadContrat(salarieId, file, orgId);

        assertThat(url).contains("/documents/contrat.pdf");
        verify(minioStorageService).upload(any(), any(), anyLong(), eq("application/pdf"));
    }

    @Test
    void uploadContrat_refuseNonPdf() throws Exception {
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(owned(StatutSalarie.ACTIF)));
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> salarieService.uploadContrat(salarieId, file, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TYPE_INVALIDE");
        verify(minioStorageService, never()).upload(any(), any(), anyLong(), any());
    }

    @Test
    void listHistoriqueSalaires_ok() {
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(owned(StatutSalarie.ACTIF)));
        HistoriqueSalaire h = grille(owned(StatutSalarie.ACTIF));
        when(historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(salarieId)).thenReturn(List.of(h));

        assertThat(salarieService.listHistoriqueSalaires(salarieId, orgId)).hasSize(1);
        assertThat(salarieService.listHistoriqueSalaires(salarieId, orgId).get(0).devise()).isEqualTo("EUR");
    }
}
