package com.app.modules.finance.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.finance.dto.FactureRequest;
import com.app.modules.finance.entity.Facture;
import com.app.modules.finance.entity.StatutFacture;
import com.app.modules.finance.repository.CategorieDepenseRepository;
import com.app.modules.finance.repository.FacturePaiementRepository;
import com.app.modules.finance.repository.FactureRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class FactureServiceTest {

    @Mock private FactureRepository factureRepository;
    @Mock private FacturePaiementRepository facturePaiementRepository;
    @Mock private CategorieDepenseRepository categorieDepenseRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private FactureSequenceService factureSequenceService;
    @Mock private TauxChangeService tauxChangeService;
    @Mock private MinioStorageService minioStorageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private FactureService factureService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Test
    void testCreerFacture_GenerateReference() throws Exception {
        when(factureSequenceService.nextSequence(orgId, 2024)).thenReturn(7);
        when(tauxChangeService.tauxVersEur(orgId, "EUR", LocalDate.of(2024, 3, 15))).thenReturn(BigDecimal.ONE);

        Utilisateur u = new Utilisateur();
        u.setId(userId);
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(u);

        Organisation org = new Organisation();
        org.setSeuilJustificatif(new BigDecimal("500"));
        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));

        when(factureRepository.save(any(Facture.class)))
                .thenAnswer(
                        inv -> {
                            Facture f = inv.getArgument(0);
                            if (f.getId() == null) {
                                f.setId(UUID.fromString("c0000000-0000-0000-0000-000000000001"));
                            }
                            return f;
                        });
        when(facturePaiementRepository.sumMontantByFactureId(any())).thenReturn(BigDecimal.ZERO);

        FactureRequest req =
                new FactureRequest(
                        "Fournisseur X",
                        LocalDate.of(2024, 3, 15),
                        new BigDecimal("100.00"),
                        new BigDecimal("20"),
                        "EUR",
                        null,
                        "BROUILLON",
                        null);

        factureService.creer(req, null, orgId, userId);

        ArgumentCaptor<Facture> cap = ArgumentCaptor.forClass(Facture.class);
        verify(factureRepository).save(cap.capture());
        assertThat(cap.getValue().getReference()).isEqualTo("FAC-2024-0007");
    }

    @Test
    void testCreerFacture_JustificatifRequis_LanceException() throws Exception {
        when(factureSequenceService.nextSequence(orgId, 2024)).thenReturn(1);
        when(tauxChangeService.tauxVersEur(orgId, "EUR", LocalDate.of(2024, 6, 1))).thenReturn(BigDecimal.ONE);

        Utilisateur u = new Utilisateur();
        u.setId(userId);
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(u);

        Organisation org = new Organisation();
        org.setSeuilJustificatif(new BigDecimal("500"));
        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));

        when(factureRepository.save(any(Facture.class)))
                .thenAnswer(
                        inv -> {
                            Facture f = inv.getArgument(0);
                            f.setId(UUID.fromString("c0000000-0000-0000-0000-000000000001"));
                            return f;
                        });

        FactureRequest req =
                new FactureRequest(
                        "Fournisseur Y",
                        LocalDate.of(2024, 6, 1),
                        new BigDecimal("1000.00"),
                        new BigDecimal("20"),
                        "EUR",
                        null,
                        "A_PAYER",
                        null);

        assertThatThrownBy(() -> factureService.creer(req, null, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "JUSTIFICATIF_REQUIS");
    }

    @Test
    void testChangerStatut_TransitionInvalide_LanceException() {
        UUID id = UUID.fromString("d0000000-0000-0000-0000-000000000001");
        Facture f = new Facture();
        f.setId(id);
        f.setOrganisationId(orgId);
        f.setMontantTtc(new BigDecimal("120.00"));
        f.setStatut(StatutFacture.BROUILLON);
        when(factureRepository.findById(id)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> factureService.changerStatut(id, "PAYE", orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FACTURE_STATUT_TRANSITION");
    }

    @Test
    void testMettreAJourStatut_PayeAuto_QuandSommeComplete() {
        UUID fid = UUID.fromString("e0000000-0000-0000-0000-000000000001");
        Facture f = new Facture();
        f.setId(fid);
        f.setMontantTtc(new BigDecimal("100.00"));
        f.setStatut(StatutFacture.A_PAYER);
        when(factureRepository.findById(fid)).thenReturn(Optional.of(f));
        when(facturePaiementRepository.sumMontantByFactureId(fid)).thenReturn(new BigDecimal("100.00"));

        factureService.mettreAJourStatutApresPaiement(fid);

        ArgumentCaptor<Facture> cap = ArgumentCaptor.forClass(Facture.class);
        verify(factureRepository).save(cap.capture());
        assertThat(cap.getValue().getStatut()).isEqualTo(StatutFacture.PAYE);
    }

    @Test
    void testModifier_FactureDejaPayee_Refuse() {
        UUID id = UUID.fromString("f0000000-0000-0000-0000-000000000001");
        Facture f = new Facture();
        f.setId(id);
        f.setOrganisationId(orgId);
        f.setStatut(StatutFacture.PAYE);
        when(factureRepository.findById(id)).thenReturn(Optional.of(f));

        FactureRequest req =
                new FactureRequest(
                        "Fournisseur Z",
                        LocalDate.of(2024, 1, 1),
                        new BigDecimal("10.00"),
                        new BigDecimal("20"),
                        "EUR",
                        null,
                        "A_PAYER",
                        null);

        assertThatThrownBy(() -> factureService.modifier(id, req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FACTURE_DEJA_PAYEE");
        verify(factureRepository, never()).save(any());
    }

    @Test
    void testChangerStatut_BrouillonVersAPayer_JustificatifRequis_Refuse() {
        UUID id = UUID.fromString("f1000000-0000-0000-0000-000000000001");
        Facture f = new Facture();
        f.setId(id);
        f.setOrganisationId(orgId);
        f.setStatut(StatutFacture.BROUILLON);
        f.setMontantTtc(new BigDecimal("1000.00"));
        f.setJustificatifUrl(null);
        when(factureRepository.findById(id)).thenReturn(Optional.of(f));

        Organisation org = new Organisation();
        org.setSeuilJustificatif(new BigDecimal("500"));
        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> factureService.changerStatut(id, "A_PAYER", orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "JUSTIFICATIF_REQUIS");
    }

    @Test
    void testUploadJustificatif_UploadOk_SetUrl() throws Exception {
        UUID id = UUID.fromString("f2000000-0000-0000-0000-000000000001");
        Facture f = new Facture();
        f.setId(id);
        f.setOrganisationId(orgId);
        f.setStatut(StatutFacture.BROUILLON);
        when(factureRepository.findById(id)).thenReturn(Optional.of(f));

        when(factureRepository.save(any(Facture.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] pdf = "%PDF-1.4\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("justificatif", "j.pdf", "application/pdf", pdf);

        factureService.uploadJustificatif(id, file, orgId, userId);

        verify(minioStorageService).upload(eq("factures/" + orgId + "/" + id + "/j.pdf"), any(), eq((long) pdf.length), eq("application/pdf"));
        ArgumentCaptor<Facture> cap = ArgumentCaptor.forClass(Facture.class);
        verify(factureRepository).save(cap.capture());
        assertThat(cap.getValue().getJustificatifUrl()).isEqualTo("factures/" + orgId + "/" + id + "/j.pdf");
    }

    @Test
    void testModifier_CategorieAbsente_Refuse() {
        UUID id = UUID.fromString("f3000000-0000-0000-0000-000000000001");
        Facture f = new Facture();
        f.setId(id);
        f.setOrganisationId(orgId);
        f.setStatut(StatutFacture.BROUILLON);
        f.setMontantTtc(new BigDecimal("120.00"));
        when(factureRepository.findById(id)).thenReturn(Optional.of(f));
        when(tauxChangeService.tauxVersEur(orgId, "EUR", LocalDate.of(2024, 2, 1))).thenReturn(BigDecimal.ONE);

        UUID catId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        when(categorieDepenseRepository.findById(catId)).thenReturn(Optional.empty());

        FactureRequest req =
                new FactureRequest(
                        "F",
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("100.00"),
                        new BigDecimal("20"),
                        "EUR",
                        catId,
                        "BROUILLON",
                        null);

        assertThatThrownBy(() -> factureService.modifier(id, req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CATEGORIE_ABSENTE");
    }
}
