package com.app.modules.rapports.service;

import com.app.audit.AuditLogService;
import com.app.modules.budget.repository.BudgetAnnuelRepository;
import com.app.modules.rapports.dto.ExportBudgetRequest;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportBudgetServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b1111111-1111-4111-8111-111111111102");

    @Mock
    private PdfBuilderService pdfBuilderService;
    @Mock
    private ExcelBuilderService excelBuilderService;
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

    @Test
    void exporterBudgetPdf_quandPasDeDonnees_lanceException() {
        ExportBudgetService svc =
                new ExportBudgetService(
                        pdfBuilderService,
                        excelBuilderService,
                        exportMinioService,
                        exportJobService,
                        configExportRepository,
                        auditLogService,
                        budgetAnnuelRepository,
                        jdbcTemplate);

        when(configExportRepository.findByOrganisationId(orgId)).thenReturn(Optional.empty());
        // loadExecutionBudget returns header-only (size <= 1) => EXPORT_DONNEES_ABSENTES
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orgId), eq(2026))).thenReturn(List.of());

        assertThatThrownBy(() -> svc.exporterBudgetPdf(new ExportBudgetRequest(2026), orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_DONNEES_ABSENTES");
    }
}

