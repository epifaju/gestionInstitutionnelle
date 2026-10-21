package com.app.modules.rapports.service;

import com.app.audit.AuditLogService;
import com.app.config.MinioProperties;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.budget.entity.BudgetAnnuel;
import com.app.modules.budget.entity.StatutBudget;
import com.app.modules.budget.repository.BudgetAnnuelRepository;
import com.app.modules.rapports.dto.ExportBudgetRequest;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.entity.ConfigExport;
import com.app.modules.rapports.entity.TypeExport;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
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
class ExportBudgetServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b1111111-1111-4111-8111-111111111102");
    private static final UUID JOB_ID = UUID.fromString("c2222222-2222-4222-8222-222222222202");

    @Mock
    private ExportMinioService exportMinioService;
    @Mock
    private ExportJobService exportJobService;
    @Mock
    private ConfigExportRepository configExportRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private BudgetAnnuelRepository budgetAnnuelRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private MinioClient minioClient;

    private ExportBudgetService service;

    @BeforeEach
    void setUp() {
        MinioProperties props = new MinioProperties();
        props.setBucket("bucket");
        Organisation org = new Organisation();
        org.setId(ORG_ID);
        org.setNom("Org Test");
        lenient().when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
        lenient().when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
        lenient()
                .when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(ORG_ID, 2026, StatutBudget.VALIDE))
                .thenReturn(Optional.empty());

        PdfBuilderService pdfBuilder =
                new PdfBuilderService(configExportRepository, organisationRepository, minioClient, props);
        ExcelBuilderService excelBuilder = new ExcelBuilderService(organisationRepository);

        service =
                new ExportBudgetService(
                        pdfBuilder,
                        excelBuilder,
                        exportMinioService,
                        exportJobService,
                        configExportRepository,
                        auditLogService,
                        budgetAnnuelRepository,
                        jdbcTemplate);
    }

    private List<Map<String, Object>> executionRows() {
        return List.of(
                Map.<String, Object>of(
                        "categorie", "Fournitures",
                        "type", "DEPENSE",
                        "montant_prevu", new BigDecimal("1000.00"),
                        "montant_realise", new BigDecimal("1100.00"),
                        "ecart", new BigDecimal("100.00"),
                        "taux_execution_pct", new BigDecimal("110.0")),
                Map.<String, Object>of(
                        "categorie", "Subventions",
                        "type", "RECETTE",
                        "montant_prevu", new BigDecimal("2000.00"),
                        "montant_realise", new BigDecimal("1500.00"),
                        "ecart", new BigDecimal("-500.00"),
                        "taux_execution_pct", new BigDecimal("75.0")),
                Map.<String, Object>of(
                        "categorie", "Salaires",
                        "type", "DEPENSE",
                        "montant_prevu", new BigDecimal("500.00"),
                        "montant_realise", new BigDecimal("400.00"),
                        "ecart", new BigDecimal("-100.00"),
                        "taux_execution_pct", new BigDecimal("80.0")));
    }

    @SuppressWarnings("unchecked")
    private void stubJdbc(List<Map<String, Object>> rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(ORG_ID), eq(2026))).thenReturn(rows);
    }

    @Test
    void exporterBudgetPdf_quandPasDeDonnees_lanceException() {
        stubJdbc(List.of());

        assertThatThrownBy(() -> service.exporterBudgetPdf(new ExportBudgetRequest(2026), ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_DONNEES_ABSENTES");
    }

    @Test
    void exporterBudgetPdf_sync_genereFichier() throws Exception {
        stubJdbc(executionRows());
        BudgetAnnuel budget = new BudgetAnnuel();
        budget.setStatut(StatutBudget.VALIDE);
        when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(ORG_ID, 2026, StatutBudget.VALIDE))
                .thenReturn(Optional.of(budget));
        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("https://budget.pdf");

        ExportJobResponse out = service.exporterBudgetPdf(new ExportBudgetRequest(2026), ORG_ID, USER_ID);

        assertThat(out.statut()).isEqualTo("TERMINE");
        assertThat(out.typeExport()).isEqualTo(TypeExport.BUDGET_PREVISIONNEL_PDF.name());
        assertThat(out.fichierUrl()).isEqualTo("https://budget.pdf");
        assertThat(out.nbLignes()).isEqualTo(3);
        verify(exportMinioService).uploadBytes(anyString(), any(byte[].class), eq("application/pdf"));
        verify(auditLogService).log(eq(ORG_ID), eq(USER_ID), eq("EXPORT"), eq("Budget"), any(), any(), anyMap());
    }

    @Test
    void exporterBudgetPdf_auDelaSeuil_creeJobAsync() {
        ConfigExport cfg = new ConfigExport();
        cfg.setSeuilLignesSyncPdf(1);
        when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(cfg));
        stubJdbc(executionRows());

        ExportJobResponse job =
                new ExportJobResponse(
                        JOB_ID, TypeExport.BUDGET_PREVISIONNEL_PDF.name(), "EN_ATTENTE", 0, null, null, null, null, null, null, null);
        when(exportJobService.creerJob(eq(ORG_ID), eq(USER_ID), eq(TypeExport.BUDGET_PREVISIONNEL_PDF), anyMap()))
                .thenReturn(job);

        ExportJobResponse out = service.exporterBudgetPdf(new ExportBudgetRequest(2026), ORG_ID, USER_ID);

        assertThat(out.id()).isEqualTo(JOB_ID);
    }

    @Test
    void exporterBudgetExcel_sync_genereFichier() throws Exception {
        stubJdbc(executionRows());
        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("https://budget.xlsx");

        ExportJobResponse out = service.exporterBudgetExcel(new ExportBudgetRequest(2026), ORG_ID, USER_ID);

        assertThat(out.statut()).isEqualTo("TERMINE");
        assertThat(out.typeExport()).isEqualTo(TypeExport.BUDGET_PREVISIONNEL_EXCEL.name());
        assertThat(out.nomFichier()).isEqualTo("budget-2026.xlsx");
        verify(exportMinioService)
                .uploadBytes(
                        anyString(),
                        any(byte[].class),
                        eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void exporterBudgetExcel_auDelaSeuil_creeJobAsync() {
        ConfigExport cfg = new ConfigExport();
        cfg.setSeuilLignesSyncExcel(1);
        when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(cfg));
        stubJdbc(executionRows());

        ExportJobResponse job =
                new ExportJobResponse(
                        JOB_ID,
                        TypeExport.BUDGET_PREVISIONNEL_EXCEL.name(),
                        "EN_ATTENTE",
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        when(exportJobService.creerJob(eq(ORG_ID), eq(USER_ID), eq(TypeExport.BUDGET_PREVISIONNEL_EXCEL), anyMap()))
                .thenReturn(job);

        assertThat(service.exporterBudgetExcel(new ExportBudgetRequest(2026), ORG_ID, USER_ID).id()).isEqualTo(JOB_ID);
    }

    @Test
    void generateAsync_excel_marqueTermine() throws Exception {
        stubJdbc(executionRows());
        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("https://async.xlsx");

        Map<String, Object> params = new HashMap<>();
        params.put("annee", 2026);

        service.generateAsync(JOB_ID, TypeExport.BUDGET_PREVISIONNEL_EXCEL, params, ORG_ID, USER_ID);

        verify(exportJobService).mettreAJourProgression(JOB_ID, 10);
        verify(exportJobService).marquerTermine(eq(JOB_ID), eq("https://async.xlsx"), anyString(), anyLong(), eq(3));
    }

    @Test
    void generateAsync_anneeAbsente_marqueErreur() {
        Map<String, Object> params = new HashMap<>();

        service.generateAsync(JOB_ID, TypeExport.BUDGET_PREVISIONNEL_PDF, params, ORG_ID, USER_ID);

        verify(exportJobService).marquerErreur(eq(JOB_ID), anyString());
    }
}
