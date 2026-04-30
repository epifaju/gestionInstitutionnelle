package com.app.modules.rapports.service;

import com.app.modules.rapports.entity.TypeExport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExportJobProcessor {

    private final ExportNotefraisService exportNotefraisService;
    private final ExportEtatPaieService exportEtatPaieService;
    private final ExportBudgetService exportBudgetService;
    private final ExportJournalAuditService exportJournalAuditService;

    @EventListener
    public void onExportJobRequested(ExportJobRequestedEvent ev) {
        TypeExport type = ev.typeExport();
        Map<String, Object> params = ev.parametres();
        UUID jobId = ev.jobId();
        UUID orgId = ev.organisationId();
        UUID userId = ev.userId();

        if (type == null) return;
        switch (type) {
            case NOTE_FRAIS_PDF -> {
                Object missionId = params != null ? params.get("missionId") : null;
                if (missionId instanceof UUID m) {
                    exportNotefraisService.generateAsync(jobId, m, orgId, userId);
                } else if (missionId instanceof String s) {
                    exportNotefraisService.generateAsync(jobId, UUID.fromString(s), orgId, userId);
                } else {
                    log.warn("Missing missionId for NOTE_FRAIS_PDF job {}", jobId);
                }
            }
            case ETAT_PAIE_PDF, ETAT_PAIE_EXCEL -> exportEtatPaieService.generateAsync(jobId, type, params, orgId, userId);
            case BUDGET_PREVISIONNEL_PDF, BUDGET_PREVISIONNEL_EXCEL -> exportBudgetService.generateAsync(jobId, type, params, orgId, userId);
            case JOURNAL_AUDIT_PDF, JOURNAL_AUDIT_EXCEL -> exportJournalAuditService.generateAsync(jobId, type, params, orgId, userId);
            case JOURNAL_AUDIT_CSV -> log.info("CSV audit export uses streaming endpoint; no async job processing.");
        }
    }
}

