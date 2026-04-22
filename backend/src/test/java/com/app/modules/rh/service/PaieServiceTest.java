package com.app.modules.rh.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.MarquerPayeRequest;
import com.app.modules.rh.entity.HistoriqueSalaire;
import com.app.modules.rh.entity.PaiementSalaire;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutPaie;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.PaiementSalaireRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaieServiceTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private SalarieRepository salarieRepository;
    @Mock private HistoriqueSalaireRepository historiqueSalaireRepository;
    @Mock private PaiementSalaireRepository paiementSalaireRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private PaieService paieService;

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
    void listMyPaie_SalarieLie_FiltreAPartirMoisEmbauche() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setDateEmbauche(LocalDate.of(2026, 4, 15));
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.of(s));

        HistoriqueSalaire h = new HistoriqueSalaire();
        h.setDateDebut(LocalDate.of(2026, 4, 1));
        h.setMontantNet(new BigDecimal("2600"));
        h.setDevise("EUR");
        when(historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(salarieId)).thenReturn(List.of(h));
        when(paiementSalaireRepository.findBySalarie_IdAndAnneeOrderByMoisAsc(salarieId, 2026)).thenReturn(List.of());
        when(paiementSalaireRepository.existsBySalarie_IdAndMoisAndAnnee(eq(salarieId), any(Integer.class), eq(2026))).thenReturn(false);

        Page<PaiementSalaire> page = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
        when(paiementSalaireRepository.findBySalarie_IdAndAnneeAndMoisGreaterThanEqualOrderByMoisAsc(
                salarieId, 2026, 4, PageRequest.of(0, 12))).thenReturn(page);

        paieService.listMyPaie(orgId, userId, 2026, PageRequest.of(0, 12));

        verify(paiementSalaireRepository)
                .findBySalarie_IdAndAnneeAndMoisGreaterThanEqualOrderByMoisAsc(salarieId, 2026, 4, PageRequest.of(0, 12));
    }

    @Test
    void getPaieAnnuelle_CreeLignesSeulementDepuisEmbauche_EtGrilleActiveAuPremierDuMois() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("Doe");
        s.setPrenom("John");
        s.setMatricule("EMP-0001");
        s.setDateEmbauche(LocalDate.of(2026, 4, 15));
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));

        HistoriqueSalaire h2 = new HistoriqueSalaire();
        h2.setDateDebut(LocalDate.of(2026, 6, 1));
        h2.setDateFin(null);
        h2.setMontantNet(new BigDecimal("3000"));
        h2.setDevise("EUR");

        HistoriqueSalaire h1 = new HistoriqueSalaire();
        h1.setDateDebut(LocalDate.of(2026, 4, 1));
        h1.setDateFin(LocalDate.of(2026, 5, 31));
        h1.setMontantNet(new BigDecimal("2600"));
        h1.setDevise("EUR");

        // repo returns DESC by dateDebut
        when(historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(salarieId)).thenReturn(List.of(h2, h1));
        when(paiementSalaireRepository.findBySalarie_IdAndAnneeOrderByMoisAsc(salarieId, 2026)).thenReturn(List.of());
        when(paiementSalaireRepository.existsBySalarie_IdAndMoisAndAnnee(eq(salarieId), any(Integer.class), eq(2026))).thenReturn(false);

        when(paiementSalaireRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paiementSalaireRepository.findBySalarie_IdAndAnneeAndMoisGreaterThanEqualOrderByMoisAsc(
                eq(salarieId), eq(2026), eq(4), any())).thenReturn(Page.empty());

        paieService.getPaieAnnuelle(salarieId, 2026, orgId, PageRequest.of(0, 12));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<PaiementSalaire>> cap = ArgumentCaptor.forClass((Class) List.class);
        verify(paiementSalaireRepository).saveAll(cap.capture());

        List<PaiementSalaire> created = cap.getValue();
        assertThat(created).isNotEmpty();
        assertThat(created).allMatch(p -> p.getMois() >= 4 && p.getMois() <= 12);

        PaiementSalaire avril = created.stream().filter(p -> p.getMois() == 4).findFirst().orElseThrow();
        assertThat(avril.getMontant()).isEqualByComparingTo(new BigDecimal("2600"));

        PaiementSalaire juin = created.stream().filter(p -> p.getMois() == 6).findFirst().orElseThrow();
        assertThat(juin.getMontant()).isEqualByComparingTo(new BigDecimal("3000"));
    }

    @Test
    void marquerPaye_SiStatutPasEnAttente_Refuse() {
        UUID paiementId = UUID.fromString("d0000000-0000-0000-0000-000000000001");
        PaiementSalaire p = new PaiementSalaire();
        p.setId(paiementId);
        p.setOrganisationId(orgId);
        p.setStatut(StatutPaie.PAYE);
        when(paiementSalaireRepository.findById(paiementId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> paieService.marquerPaye(paiementId, new MarquerPayeRequest(LocalDate.now(), "VIREMENT", null), orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAIEMENT_STATUT_INVALIDE");
    }

    @Test
    void annuler_SiStatutPasEnAttente_Refuse() {
        UUID paiementId = UUID.fromString("e0000000-0000-0000-0000-000000000001");
        PaiementSalaire p = new PaiementSalaire();
        p.setId(paiementId);
        p.setOrganisationId(orgId);
        p.setStatut(StatutPaie.PAYE);
        when(paiementSalaireRepository.findById(paiementId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> paieService.annuler(paiementId, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAIEMENT_STATUT_INVALIDE");
        verify(paiementSalaireRepository, never()).save(any());
    }
}

