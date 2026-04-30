package com.app.modules.rapports.service;

import com.app.audit.AuditLogRepository;
import com.app.audit.AuditLogService;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rapports.dto.ExportJournalAuditRequest;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJournalAuditServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b1111111-1111-4111-8111-111111111103");

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
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditLogService auditLogService;

    @Test
    void exporterJournalPdf_periodeTropLarge_lanceException() {
        ExportJournalAuditService svc =
                new ExportJournalAuditService(
                        pdfBuilderService,
                        excelBuilderService,
                        exportMinioService,
                        exportJobService,
                        configExportRepository,
                        auditLogRepository,
                        auditLogService);

        ExportJournalAuditRequest req =
                new ExportJournalAuditRequest(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2027, 3, 1),
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> svc.exporterJournalPdf(req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PERIODE_TROP_LARGE");
    }
}

