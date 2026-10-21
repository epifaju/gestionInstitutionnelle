package com.app.modules.rapports.service;

import com.app.audit.AuditLogRepository;
import com.app.audit.AuditLogService;
import com.app.audit.entity.AuditLog;
import com.app.config.MinioProperties;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.dto.ExportJournalAuditRequest;
import com.app.modules.rapports.entity.ConfigExport;
import com.app.modules.rapports.entity.TypeExport;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJournalAuditServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b1111111-1111-4111-8111-111111111103");
    private static final UUID JOB_ID = UUID.fromString("c2222222-2222-4222-8222-222222222203");

    @Mock
    private ExportMinioService exportMinioService;
    @Mock
    private ExportJobService exportJobService;
    @Mock
    private ConfigExportRepository configExportRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private MinioClient minioClient;

    private ExportJournalAuditService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MinioProperties props = new MinioProperties();
        props.setBucket("bucket");
        Organisation org = new Organisation();
        org.setId(ORG_ID);
        org.setNom("Org Test");
        lenient().when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
        lenient().when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());

        PdfBuilderService pdfBuilder =
                new PdfBuilderService(configExportRepository, organisationRepository, minioClient, props);
        ExcelBuilderService excelBuilder = new ExcelBuilderService(organisationRepository);

        service =
                new ExportJournalAuditService(
                        pdfBuilder,
                        excelBuilder,
                        exportMinioService,
                        exportJobService,
                        configExportRepository,
                        auditLogRepository,
                        auditLogService);
    }

    private ExportJournalAuditRequest req(LocalDate debut, LocalDate fin) {
        return new ExportJournalAuditRequest(debut, fin, "Facture", "UPDATE", null);
    }

    private AuditLog sampleLog(String action) {
        AuditLog a = new AuditLog();
        a.setId(UUID.randomUUID());
        a.setOrganisationId(ORG_ID);
        a.setUtilisateurId(USER_ID);
        a.setAction(action);
        a.setEntite("Facture");
        a.setEntiteId(UUID.fromString("d3333333-3333-4333-8333-333333333301"));
        a.setIpAddress("10.0.0.1");
        a.setDateAction(Instant.parse("2026-01-15T12:00:00Z"));
        ObjectNode avant = objectMapper.createObjectNode();
        avant.put("statut", "BROUILLON");
        ObjectNode apres = objectMapper.createObjectNode();
        apres.put("statut", "VALIDEE");
        a.setAvant(avant);
        a.setApres(apres);
        return a;
    }

    @Test
    void exporterJournalPdf_periodeTropLarge_lanceException() {
        ExportJournalAuditRequest req =
                new ExportJournalAuditRequest(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 3, 1), null, null, null);

        assertThatThrownBy(() -> service.exporterJournalPdf(req, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PERIODE_TROP_LARGE");
    }

    @Test
    void exporterJournalPdf_periodeInvalide_lanceException() {
        ExportJournalAuditRequest req =
                new ExportJournalAuditRequest(
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, null);

        assertThatThrownBy(() -> service.exporterJournalPdf(req, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_PERIODE_INVALIDE");
    }

    @Test
    void exporterJournalPdf_sansDonnees_lanceException() {
        when(auditLogRepository.countForExport(eq(ORG_ID), any(), any(), any(), any(), any())).thenReturn(0L);

        assertThatThrownBy(
                        () ->
                                service.exporterJournalPdf(
                                        req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_DONNEES_ABSENTES");
    }

    @Test
    void exporterJournalPdf_sync_genereFichier() throws Exception {
        when(auditLogRepository.countForExport(eq(ORG_ID), any(), any(), any(), any(), any())).thenReturn(1L);
        when(auditLogRepository.findForExport(eq(ORG_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleLog("UPDATE"), sampleLog("CREATE")), PageRequest.of(0, 5000), 2));
        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("https://presign/pdf");

        ExportJobResponse out =
                service.exporterJournalPdf(req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), ORG_ID, USER_ID);

        assertThat(out.statut()).isEqualTo("TERMINE");
        assertThat(out.typeExport()).isEqualTo(TypeExport.JOURNAL_AUDIT_PDF.name());
        assertThat(out.fichierUrl()).isEqualTo("https://presign/pdf");
        assertThat(out.nbLignes()).isEqualTo(2);
        verify(exportMinioService).uploadBytes(anyString(), any(byte[].class), eq("application/pdf"));
        verify(auditLogService).log(eq(ORG_ID), eq(USER_ID), eq("EXPORT"), eq("JournalAudit"), any(), any(), anyMap());
    }

    @Test
    void exporterJournalPdf_auDelaSeuil_creeJobAsync() {
        ConfigExport cfg = new ConfigExport();
        cfg.setSeuilLignesSyncPdf(1);
        when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(cfg));
        when(auditLogRepository.countForExport(eq(ORG_ID), any(), any(), any(), any(), any())).thenReturn(5L);

        ExportJobResponse job =
                new ExportJobResponse(JOB_ID, TypeExport.JOURNAL_AUDIT_PDF.name(), "EN_ATTENTE", 0, null, null, null, null, null, null, null);
        when(exportJobService.creerJob(eq(ORG_ID), eq(USER_ID), eq(TypeExport.JOURNAL_AUDIT_PDF), anyMap()))
                .thenReturn(job);

        ExportJobResponse out =
                service.exporterJournalPdf(req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), ORG_ID, USER_ID);

        assertThat(out.id()).isEqualTo(JOB_ID);
        verify(exportJobService).creerJob(eq(ORG_ID), eq(USER_ID), eq(TypeExport.JOURNAL_AUDIT_PDF), anyMap());
    }

    @Test
    void exporterJournalExcel_sync_genereFichier() throws Exception {
        when(auditLogRepository.countForExport(eq(ORG_ID), any(), any(), any(), any(), any())).thenReturn(1L);
        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("https://presign/xlsx");

        AuditLog del = sampleLog("DELETE");
        del.setApres(null);
        when(auditLogRepository.findForExport(eq(ORG_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(del), PageRequest.of(0, 5000), 1));

        ExportJobResponse out =
                service.exporterJournalExcel(req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), ORG_ID, USER_ID);

        assertThat(out.statut()).isEqualTo("TERMINE");
        assertThat(out.typeExport()).isEqualTo(TypeExport.JOURNAL_AUDIT_EXCEL.name());
        assertThat(out.fichierUrl()).isEqualTo("https://presign/xlsx");
        verify(exportMinioService)
                .uploadBytes(
                        anyString(),
                        any(byte[].class),
                        eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void exporterJournalExcel_auDelaSeuil_creeJobAsync() {
        ConfigExport cfg = new ConfigExport();
        cfg.setSeuilLignesSyncExcel(1);
        when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(cfg));
        when(auditLogRepository.countForExport(eq(ORG_ID), any(), any(), any(), any(), any())).thenReturn(10L);

        ExportJobResponse job =
                new ExportJobResponse(JOB_ID, TypeExport.JOURNAL_AUDIT_EXCEL.name(), "EN_ATTENTE", 0, null, null, null, null, null, null, null);
        when(exportJobService.creerJob(eq(ORG_ID), eq(USER_ID), eq(TypeExport.JOURNAL_AUDIT_EXCEL), anyMap()))
                .thenReturn(job);

        ExportJobResponse out =
                service.exporterJournalExcel(req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), ORG_ID, USER_ID);

        assertThat(out.id()).isEqualTo(JOB_ID);
    }

    @Test
    void generateAsync_pdf_marqueTermine() throws Exception {
        when(auditLogRepository.findForExport(eq(ORG_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleLog("LOGIN")), PageRequest.of(0, 5000), 1));
        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("https://async");

        Map<String, Object> params = new HashMap<>();
        params.put("dateDebut", "2026-01-01");
        params.put("dateFin", "2026-01-31");
        params.put("entite", null);
        params.put("action", null);
        params.put("utilisateurId", null);

        service.generateAsync(JOB_ID, TypeExport.JOURNAL_AUDIT_PDF, params, ORG_ID, USER_ID);

        verify(exportJobService).mettreAJourProgression(JOB_ID, 10);
        verify(exportJobService).marquerTermine(eq(JOB_ID), eq("https://async"), anyString(), anyLong(), eq(1));
    }

    @Test
    void generateAsync_enErreur_marqueErreur() {
        when(auditLogRepository.findForExport(eq(ORG_ID), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        Map<String, Object> params = new HashMap<>();
        params.put("dateDebut", "2026-01-01");
        params.put("dateFin", "2026-01-31");
        params.put("entite", " ");
        params.put("action", "  ");
        params.put("utilisateurId", USER_ID.toString());

        service.generateAsync(JOB_ID, TypeExport.JOURNAL_AUDIT_EXCEL, params, ORG_ID, USER_ID);

        verify(exportJobService).marquerErreur(JOB_ID, "boom");
    }
}
