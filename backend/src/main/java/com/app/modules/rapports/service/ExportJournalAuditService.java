package com.app.modules.rapports.service;

import com.app.audit.AuditLogRepository;
import com.app.audit.entity.AuditLog;
import com.app.audit.AuditLogService;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.dto.ExportJournalAuditRequest;
import com.app.modules.rapports.entity.TypeExport;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportJournalAuditService {

    private final PdfBuilderService pdfBuilderService;
    private final ExcelBuilderService excelBuilderService;
    private final ExportMinioService exportMinioService;
    private final ExportJobService exportJobService;
    private final ConfigExportRepository configExportRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public ExportJobResponse exporterJournalPdf(ExportJournalAuditRequest req, UUID orgId, UUID userId) {
        validatePeriode(req);
        int seuil = configExportRepository.findByOrganisationId(orgId).map(c -> c.getSeuilLignesSyncPdf()).orElse(500);
        long count = count(req, orgId);
        if (count == 0) throw BusinessException.badRequest("EXPORT_DONNEES_ABSENTES");
        if (count <= seuil) {
            return generatePdfSync(req, orgId, userId);
        }
        Map<String, Object> params = toParams(req);
        return exportJobService.creerJob(orgId, userId, TypeExport.JOURNAL_AUDIT_PDF, params);
    }

    @Transactional(readOnly = true)
    public ExportJobResponse exporterJournalExcel(ExportJournalAuditRequest req, UUID orgId, UUID userId) {
        validatePeriode(req);
        int seuil = configExportRepository.findByOrganisationId(orgId).map(c -> c.getSeuilLignesSyncExcel()).orElse(5000);
        long count = count(req, orgId);
        if (count == 0) throw BusinessException.badRequest("EXPORT_DONNEES_ABSENTES");
        if (count <= seuil) {
            return generateExcelSync(req, orgId, userId);
        }
        Map<String, Object> params = toParams(req);
        return exportJobService.creerJob(orgId, userId, TypeExport.JOURNAL_AUDIT_EXCEL, params);
    }

    @Async("exportExecutor")
    public void generateAsync(UUID jobId, TypeExport type, Map<String, Object> params, UUID orgId, UUID userId) {
        try {
            exportJobService.mettreAJourProgression(jobId, 10);
            ExportJournalAuditRequest req = fromParams(params);
            ExportJobResponse r =
                    (type == TypeExport.JOURNAL_AUDIT_PDF)
                            ? generatePdfSync(req, orgId, userId)
                            : generateExcelSync(req, orgId, userId);
            exportJobService.marquerTermine(jobId, r.fichierUrl(), r.nomFichier(), r.tailleOctets(), r.nbLignes());
        } catch (Exception e) {
            log.error("Async JOURNAL_AUDIT failed jobId={}", jobId, e);
            exportJobService.marquerErreur(jobId, e.getMessage());
        }
    }

    private void validatePeriode(ExportJournalAuditRequest req) {
        if (req.dateDebut().isAfter(req.dateFin())) throw BusinessException.badRequest("EXPORT_PERIODE_INVALIDE");
        long days = ChronoUnit.DAYS.between(req.dateDebut(), req.dateFin());
        if (days > 366) throw BusinessException.badRequest("PERIODE_TROP_LARGE");
    }

    private long count(ExportJournalAuditRequest req, UUID orgId) {
        Instant start = req.dateDebut().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = req.dateFin().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return auditLogRepository.countForExport(orgId, start, end, blankToNull(req.entite()), blankToNull(req.action()), req.utilisateurId());
    }

    @Transactional
    protected ExportJobResponse generatePdfSync(ExportJournalAuditRequest req, UUID orgId, UUID userId) {
        Instant start = req.dateDebut().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = req.dateFin().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Pageable p = PageRequest.of(0, 5000);
        List<AuditLog> logs =
                auditLogRepository
                        .findForExport(orgId, start, end, blankToNull(req.entite()), blankToNull(req.action()), req.utilisateurId(), p)
                        .getContent();

        PdfDocument pdfDoc = pdfBuilderService.creerDocument(orgId, "JOURNAL D'AUDIT");
        Document doc = new Document(pdfDoc);
        doc.setMargins(80, 30, 40, 30);

        doc.add(new Paragraph("Période : " + req.dateDebut() + " au " + req.dateFin()).setItalic());
        doc.add(new Paragraph("Filtres : entité=" + n(req.entite()) + ", action=" + n(req.action()) + ", utilisateur=" + (req.utilisateurId() == null ? "tous" : req.utilisateurId())));

        pdfBuilderService.ajouterSection(doc, "Statistiques de la période");
        long total = logs.size();
        long create = logs.stream().filter(l -> "CREATE".equals(l.getAction())).count();
        long update = logs.stream().filter(l -> "UPDATE".equals(l.getAction())).count();
        long delete = logs.stream().filter(l -> "DELETE".equals(l.getAction())).count();
        long login = logs.stream().filter(l -> "LOGIN".equals(l.getAction())).count();

        Table stats = new Table(new float[] {40, 60}).useAllAvailableWidth();
        stats.addCell(k("Total événements"));
        stats.addCell(v(String.valueOf(total)));
        stats.addCell(k("Créations (CREATE)"));
        stats.addCell(v(String.valueOf(create)));
        stats.addCell(k("Modifications (UPDATE)"));
        stats.addCell(v(String.valueOf(update)));
        stats.addCell(k("Suppressions (DELETE)"));
        stats.addCell(kv(String.valueOf(delete)));
        stats.addCell(k("Connexions (LOGIN)"));
        stats.addCell(v(String.valueOf(login)));
        doc.add(stats);

        pdfBuilderService.ajouterSection(doc, "Détail des événements");
        Table t =
                pdfBuilderService.creerTableau(
                        new String[] {"Date/heure", "Utilisateur", "Action", "Entité", "ID", "IP", "Résumé changement"},
                        new float[] {14, 14, 8, 12, 10, 10, 32});
        for (AuditLog a : logs) {
            String dt = a.getDateAction() != null ? a.getDateAction().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : "";
            String id = a.getEntiteId() != null ? a.getEntiteId().toString().substring(0, 8) : "";
            t.addCell(c(dt));
            t.addCell(c(a.getUtilisateurId() != null ? a.getUtilisateurId().toString().substring(0, 8) : ""));
            t.addCell(c(n(a.getAction())));
            t.addCell(c(n(a.getEntite())));
            t.addCell(c(id));
            t.addCell(c(n(a.getIpAddress())));
            t.addCell(c(diffSummary(a)));
        }
        doc.add(t);

        doc.add(new Paragraph(
                        "NOTE LÉGALE : Ce journal est généré depuis le système d'audit automatique. Toute modification de ce document en altère la valeur probatoire.")
                .setFontSize(9)
                .setItalic());

        byte[] bytes = pdfBuilderService.finaliser(pdfDoc, doc);
        String objectName = orgId + "/exports/journal-audit/pdf/" + System.currentTimeMillis() + ".pdf";
        try {
            exportMinioService.uploadBytes(objectName, bytes, "application/pdf");
            String url = exportMinioService.presignGet(objectName, 48 * 3600);
            auditLogService.log(orgId, userId, "EXPORT", "JournalAudit", null, null, Map.of("type", TypeExport.JOURNAL_AUDIT_PDF.name()));
            return new ExportJobResponse(
                    null,
                    TypeExport.JOURNAL_AUDIT_PDF.name(),
                    "TERMINE",
                    100,
                    url,
                    "journal-audit-" + LocalDate.now() + ".pdf",
                    (long) bytes.length,
                    logs.size(),
                    null,
                    null,
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            throw new BusinessException("EXPORT_MINIO_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Erreur lors de l'upload MinIO.");
        }
    }

    @Transactional
    protected ExportJobResponse generateExcelSync(ExportJournalAuditRequest req, UUID orgId, UUID userId) {
        Instant start = req.dateDebut().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = req.dateFin().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Pageable p = PageRequest.of(0, 5000);
        List<AuditLog> logs =
                auditLogRepository
                        .findForExport(orgId, start, end, blankToNull(req.entite()), blankToNull(req.action()), req.utilisateurId(), p)
                        .getContent();

        Workbook wb = excelBuilderService.creerClasseur("Journal");
        Sheet s = wb.getSheetAt(0);

        String[] headers = {"Date", "Heure", "Utilisateur", "Action", "Entité", "ID entité", "Champs modifiés", "Avant", "Après", "IP"};
        int[] widths = {12 * 256, 10 * 256, 18 * 256, 10 * 256, 16 * 256, 14 * 256, 26 * 256, 28 * 256, 28 * 256, 14 * 256};
        excelBuilderService.creerLigneEntete(s, 0, headers, widths);

        CellStyle data = excelBuilderService.getStyleDonnees();
        int r = 1;
        for (AuditLog a : logs) {
            Row row = s.createRow(r++);
            var dt = a.getDateAction() != null ? a.getDateAction().atZone(ZoneId.systemDefault()).toLocalDateTime() : null;
            int c = 0;
            text(row, c++, dt != null ? dt.toLocalDate().toString() : "", data);
            text(row, c++, dt != null ? dt.toLocalTime().toString() : "", data);
            text(row, c++, a.getUtilisateurId() != null ? a.getUtilisateurId().toString() : "", data);
            text(row, c++, n(a.getAction()), data);
            text(row, c++, n(a.getEntite()), data);
            text(row, c++, a.getEntiteId() != null ? a.getEntiteId().toString() : "", data);
            text(row, c++, diffFields(a), data);
            text(row, c++, a.getAvant() != null ? a.getAvant().toString() : "", data);
            text(row, c++, a.getApres() != null ? a.getApres().toString() : "", data);
            text(row, c++, n(a.getIpAddress()), data);
        }

        String periode = req.dateDebut() + " → " + req.dateFin();
        excelBuilderService.ajouterOngletInfos(wb, orgId, TypeExport.JOURNAL_AUDIT_EXCEL.name(), periode, logs.size());
        byte[] bytes = excelBuilderService.finaliser(wb);

        String objectName = orgId + "/exports/journal-audit/excel/" + System.currentTimeMillis() + ".xlsx";
        try {
            exportMinioService.uploadBytes(objectName, bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String url = exportMinioService.presignGet(objectName, 48 * 3600);
            auditLogService.log(orgId, userId, "EXPORT", "JournalAudit", null, null, Map.of("type", TypeExport.JOURNAL_AUDIT_EXCEL.name()));
            return new ExportJobResponse(
                    null,
                    TypeExport.JOURNAL_AUDIT_EXCEL.name(),
                    "TERMINE",
                    100,
                    url,
                    "journal-audit-" + LocalDate.now() + ".xlsx",
                    (long) bytes.length,
                    logs.size(),
                    null,
                    null,
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            throw new BusinessException("EXPORT_MINIO_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Erreur lors de l'upload MinIO.");
        }
    }

    private static Map<String, Object> toParams(ExportJournalAuditRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("dateDebut", req.dateDebut().toString());
        p.put("dateFin", req.dateFin().toString());
        p.put("entite", req.entite());
        p.put("action", req.action());
        p.put("utilisateurId", req.utilisateurId() != null ? req.utilisateurId().toString() : null);
        return p;
    }

    private static ExportJournalAuditRequest fromParams(Map<String, Object> p) {
        LocalDate d1 = LocalDate.parse(String.valueOf(p.get("dateDebut")));
        LocalDate d2 = LocalDate.parse(String.valueOf(p.get("dateFin")));
        String entite = (String) p.get("entite");
        String action = (String) p.get("action");
        String u = (String) p.get("utilisateurId");
        UUID uid = (u == null || u.isBlank()) ? null : UUID.fromString(u);
        return new ExportJournalAuditRequest(d1, d2, entite, action, uid);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isBlank() ? null : v;
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }

    private static Cell k(String s) {
        return new Cell().add(new Paragraph(s).setBold()).setPadding(4);
    }

    private static Cell kv(String s) {
        return new Cell().add(new Paragraph(s)).setPadding(4);
    }

    private static Cell v(String s) {
        return new Cell().add(new Paragraph(s == null ? "" : s)).setPadding(4);
    }

    private static Cell c(String s) {
        return new Cell().add(new Paragraph(s == null ? "" : s)).setPadding(4);
    }

    private static void text(Row r, int idx, String val, CellStyle style) {
        var c = r.createCell(idx);
        c.setCellValue(val == null ? "" : val);
        c.setCellStyle(style);
    }

    private static String diffFields(AuditLog a) {
        if (a.getAvant() == null && a.getApres() != null) return "Création";
        if (a.getAvant() != null && a.getApres() == null) return "Suppression";
        if (a.getAvant() == null) return "";
        var fields = new java.util.ArrayList<String>();
        var before = a.getAvant();
        var after = a.getApres();
        if (before != null && after != null && before.isObject() && after.isObject()) {
            before.fieldNames().forEachRemaining(
                    f -> {
                        var b = before.get(f);
                        var n = after.get(f);
                        if (n != null && (b == null || !b.equals(n))) {
                            fields.add(f);
                        }
                    });
        }
        return String.join(",", fields);
    }

    private static String diffSummary(AuditLog a) {
        if (a.getAvant() == null && a.getApres() != null) return "Création";
        if (a.getAvant() != null && a.getApres() == null) return "Suppression";
        if (a.getAvant() == null || a.getApres() == null) return "";
        if (!a.getAvant().isObject() || !a.getApres().isObject()) return "";
        var before = a.getAvant();
        var after = a.getApres();
        var parts = new java.util.ArrayList<String>();
        before.fieldNames().forEachRemaining(
                f -> {
                    var b = before.get(f);
                    var n = after.get(f);
                    if (n != null && (b == null || !b.equals(n))) {
                        String left = b == null ? "null" : b.asText();
                        String right = n.asText();
                        parts.add(f + " : " + left + " → " + right);
                    }
                });
        if (parts.isEmpty()) return "";
        if (parts.size() > 3) {
            return String.join(" | ", parts.subList(0, 3)) + " | …";
        }
        return String.join(" | ", parts);
    }
}

