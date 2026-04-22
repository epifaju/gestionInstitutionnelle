package com.app.modules.finance.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.finance.dto.PaiementLigneRequest;
import com.app.modules.finance.dto.PaiementRequest;
import com.app.modules.finance.entity.Facture;
import com.app.modules.finance.entity.FacturePaiement;
import com.app.modules.finance.entity.Paiement;
import com.app.modules.finance.entity.StatutFacture;
import com.app.modules.finance.repository.FacturePaiementRepository;
import com.app.modules.finance.repository.FactureRepository;
import com.app.modules.finance.repository.PaiementRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class PaiementServiceTest {

    @Mock private PaiementRepository paiementRepository;
    @Mock private FactureRepository factureRepository;
    @Mock private FacturePaiementRepository facturePaiementRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private FactureService factureService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private PaiementService paiementService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID otherOrgId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Test
    void enregistrer_totalIncoherent_refuse() {
        UUID fid = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        PaiementRequest req =
                new PaiementRequest(
                        LocalDate.of(2026, 4, 22),
                        new BigDecimal("100.00"),
                        "EUR",
                        "Compte",
                        "VIREMENT",
                        List.of(new PaiementLigneRequest(fid, new BigDecimal("90.00"))),
                        null);

        assertThatThrownBy(() -> paiementService.enregistrer(req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAIEMENT_TOTAL_INCOHERENT");

        verify(paiementRepository, never()).save(any());
    }

    @Test
    void enregistrer_factureAutreOrg_refuse() {
        UUID pid = UUID.fromString("d0000000-0000-0000-0000-000000000001");
        UUID fid = UUID.fromString("c0000000-0000-0000-0000-000000000001");

        when(utilisateurRepository.getReferenceById(userId)).thenReturn(new Utilisateur());
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(inv -> {
            Paiement p = inv.getArgument(0);
            p.setId(pid);
            return p;
        });

        Facture f = new Facture();
        f.setId(fid);
        f.setOrganisationId(otherOrgId);
        f.setStatut(StatutFacture.A_PAYER);
        f.setMontantTtc(new BigDecimal("100.00"));
        when(factureRepository.findById(fid)).thenReturn(Optional.of(f));

        PaiementRequest req =
                new PaiementRequest(
                        LocalDate.of(2026, 4, 22),
                        new BigDecimal("50.00"),
                        "EUR",
                        "Compte",
                        "VIREMENT",
                        List.of(new PaiementLigneRequest(fid, new BigDecimal("50.00"))),
                        null);

        assertThatThrownBy(() -> paiementService.enregistrer(req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FACTURE_ORG_MISMATCH");
    }

    @Test
    void enregistrer_factureNonPayable_refuse() {
        UUID pid = UUID.fromString("d1000000-0000-0000-0000-000000000001");
        UUID fid = UUID.fromString("c1000000-0000-0000-0000-000000000001");

        when(utilisateurRepository.getReferenceById(userId)).thenReturn(new Utilisateur());
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(inv -> {
            Paiement p = inv.getArgument(0);
            p.setId(pid);
            return p;
        });

        Facture f = new Facture();
        f.setId(fid);
        f.setOrganisationId(orgId);
        f.setStatut(StatutFacture.BROUILLON);
        f.setMontantTtc(new BigDecimal("100.00"));
        when(factureRepository.findById(fid)).thenReturn(Optional.of(f));

        PaiementRequest req =
                new PaiementRequest(
                        LocalDate.of(2026, 4, 22),
                        new BigDecimal("50.00"),
                        "EUR",
                        "Compte",
                        "VIREMENT",
                        List.of(new PaiementLigneRequest(fid, new BigDecimal("50.00"))),
                        null);

        assertThatThrownBy(() -> paiementService.enregistrer(req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FACTURE_NON_PAYABLE");
    }

    @Test
    void enregistrer_montantDepasse_refuse() {
        UUID pid = UUID.fromString("d2000000-0000-0000-0000-000000000001");
        UUID fid = UUID.fromString("c2000000-0000-0000-0000-000000000001");

        when(utilisateurRepository.getReferenceById(userId)).thenReturn(new Utilisateur());
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(inv -> {
            Paiement p = inv.getArgument(0);
            p.setId(pid);
            return p;
        });

        Facture f = new Facture();
        f.setId(fid);
        f.setOrganisationId(orgId);
        f.setStatut(StatutFacture.A_PAYER);
        f.setMontantTtc(new BigDecimal("100.00"));
        when(factureRepository.findById(fid)).thenReturn(Optional.of(f));
        when(facturePaiementRepository.sumMontantByFactureId(fid)).thenReturn(new BigDecimal("80.00"));

        PaiementRequest req =
                new PaiementRequest(
                        LocalDate.of(2026, 4, 22),
                        new BigDecimal("30.00"),
                        "EUR",
                        "Compte",
                        "VIREMENT",
                        List.of(new PaiementLigneRequest(fid, new BigDecimal("30.00"))),
                        null);

        assertThatThrownBy(() -> paiementService.enregistrer(req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAIEMENT_MONTANT_DEPASSE");
    }

    @Test
    void enregistrer_ok_creeLigneEtMetAJourStatut() {
        UUID pid = UUID.fromString("d3000000-0000-0000-0000-000000000001");
        UUID fid = UUID.fromString("c3000000-0000-0000-0000-000000000001");

        when(utilisateurRepository.getReferenceById(userId)).thenReturn(new Utilisateur());
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(inv -> {
            Paiement p = inv.getArgument(0);
            if (p.getId() == null) p.setId(pid);
            return p;
        });

        Facture f = new Facture();
        f.setId(fid);
        f.setOrganisationId(orgId);
        f.setStatut(StatutFacture.A_PAYER);
        f.setMontantTtc(new BigDecimal("100.00"));
        when(factureRepository.findById(fid)).thenReturn(Optional.of(f));
        when(facturePaiementRepository.sumMontantByFactureId(fid)).thenReturn(BigDecimal.ZERO);

        PaiementRequest req =
                new PaiementRequest(
                        LocalDate.of(2026, 4, 22),
                        new BigDecimal("40.00"),
                        "EUR",
                        "Compte",
                        "VIREMENT",
                        List.of(new PaiementLigneRequest(fid, new BigDecimal("40.00"))),
                        null);

        paiementService.enregistrer(req, orgId, userId);

        ArgumentCaptor<FacturePaiement> cap = ArgumentCaptor.forClass(FacturePaiement.class);
        verify(facturePaiementRepository).save(cap.capture());
        assertThat(cap.getValue().getPaiementId()).isEqualTo(pid);
        assertThat(cap.getValue().getFactureId()).isEqualTo(fid);
        assertThat(cap.getValue().getMontant()).isEqualByComparingTo(new BigDecimal("40.00"));

        verify(factureService).mettreAJourStatutApresPaiement(fid);
        verify(auditLogService).log(eq(orgId), eq(userId), eq("CREATE"), eq("Paiement"), eq(pid), eq(null), any());
    }
}

