package com.app.modules.finance.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.finance.dto.RecetteRequest;
import com.app.modules.finance.entity.Recette;
import com.app.modules.finance.entity.TypeRecette;
import com.app.modules.finance.repository.CategorieDepenseRepository;
import com.app.modules.finance.repository.RecetteRepository;
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
class RecetteServiceTest {

    @Mock private RecetteRepository recetteRepository;
    @Mock private CategorieDepenseRepository categorieDepenseRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private TauxChangeService tauxChangeService;
    @Mock private MinioStorageService minioStorageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private RecetteService recetteService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private final UUID recetteId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Test
    void creer_AvecJustificatifPdf_UploadEtSauveUrl() throws Exception {
        when(tauxChangeService.tauxVersEur(orgId, "EUR", LocalDate.of(2026, 4, 1))).thenReturn(BigDecimal.ONE);

        Utilisateur u = new Utilisateur();
        u.setId(userId);
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(u);

        when(recetteRepository.save(any(Recette.class)))
                .thenAnswer(inv -> {
                    Recette r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(recetteId);
                    return r;
                });

        RecetteRequest req = new RecetteRequest(
                LocalDate.of(2026, 4, 1),
                new BigDecimal("100.00"),
                "EUR",
                TypeRecette.DON.name(),
                "desc",
                "ESPECES",
                null);

        byte[] pdf = "%PDF-1.4\n%âãÏÓ\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("justificatif", "justif.pdf", "application/pdf", pdf);

        var res = recetteService.creer(req, file, orgId, userId);

        verify(minioStorageService).upload(eq("recettes/" + orgId + "/" + recetteId + "/justif.pdf"), any(), eq((long) pdf.length), eq("application/pdf"));
        assertThat(res.justificatifUrl()).isEqualTo("/api/v1/finance/recettes/" + recetteId + "/justificatif");
    }

    @Test
    void creer_FichierTypeInvalide_Refuse() throws Exception {
        when(tauxChangeService.tauxVersEur(orgId, "EUR", LocalDate.of(2026, 4, 1))).thenReturn(BigDecimal.ONE);
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(u);
        when(recetteRepository.save(any(Recette.class)))
                .thenAnswer(inv -> {
                    Recette r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(recetteId);
                    return r;
                });

        RecetteRequest req = new RecetteRequest(
                LocalDate.of(2026, 4, 1),
                new BigDecimal("100.00"),
                "EUR",
                TypeRecette.DON.name(),
                "desc",
                "ESPECES",
                null);

        MockMultipartFile file = new MockMultipartFile("justificatif", "script.sh", "text/plain", "echo hi".getBytes());

        assertThatThrownBy(() -> recetteService.creer(req, file, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TYPE_INVALIDE");
    }

    @Test
    void getJustificatifObjectName_PrefixInvalide_Refuse() {
        UUID id = UUID.fromString("d0000000-0000-0000-0000-000000000001");
        Recette r = new Recette();
        r.setId(id);
        r.setOrganisationId(orgId);
        r.setJustificatifUrl("recettes/" + orgId + "/AUTRE/" + "x.pdf");
        when(recetteRepository.findById(id)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> recetteService.getJustificatifObjectName(id, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "JUSTIFICATIF_ORG_MISMATCH");
    }

    @Test
    void uploadJustificatif_SupprimeAncien_SiDifferent_BestEffort() throws Exception {
        UUID id = UUID.fromString("e0000000-0000-0000-0000-000000000001");
        Recette r = new Recette();
        r.setId(id);
        r.setOrganisationId(orgId);
        r.setJustificatifUrl("recettes/" + orgId + "/" + id + "/old.pdf");
        when(recetteRepository.findById(id)).thenReturn(Optional.of(r));

        // will save updated recette
        when(recetteRepository.save(any(Recette.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] pdf = "%PDF-1.4\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("justificatif", "new.pdf", "application/pdf", pdf);

        recetteService.uploadJustificatif(id, file, orgId, userId);

        verify(minioStorageService).upload(eq("recettes/" + orgId + "/" + id + "/new.pdf"), any(), eq((long) pdf.length), eq("application/pdf"));
        verify(minioStorageService).delete("recettes/" + orgId + "/" + id + "/old.pdf");
        ArgumentCaptor<Recette> cap = ArgumentCaptor.forClass(Recette.class);
        verify(recetteRepository).save(cap.capture());
        assertThat(cap.getValue().getJustificatifUrl()).isEqualTo("recettes/" + orgId + "/" + id + "/new.pdf");
    }

    @Test
    void supprimer_SupprimeEtTenteDeleteMinio_BestEffort() throws Exception {
        UUID id = UUID.fromString("f0000000-0000-0000-0000-000000000001");
        Recette r = new Recette();
        r.setId(id);
        r.setOrganisationId(orgId);
        r.setJustificatifUrl("recettes/" + orgId + "/" + id + "/x.pdf");
        when(recetteRepository.findById(id)).thenReturn(Optional.of(r));

        recetteService.supprimer(id, orgId, userId);

        verify(recetteRepository).delete(r);
        verify(minioStorageService).delete("recettes/" + orgId + "/" + id + "/x.pdf");
        verify(auditLogService).log(eq(orgId), eq(userId), eq("DELETE"), eq("Recette"), eq(id), any(), eq(null));
    }

    @Test
    void uploadJustificatif_FichierAbsent_Refuse() throws Exception {
        UUID id = UUID.fromString("f1000000-0000-0000-0000-000000000001");
        Recette r = new Recette();
        r.setId(id);
        r.setOrganisationId(orgId);
        when(recetteRepository.findById(id)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> recetteService.uploadJustificatif(id, null, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_ABSENT");

        verify(minioStorageService, never()).upload(any(), any(), any(Long.class), any());
    }
}

