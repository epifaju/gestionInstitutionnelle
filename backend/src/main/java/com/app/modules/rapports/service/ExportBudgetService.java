package com.app.modules.rapports.service;

import com.app.audit.AuditLogService;
import com.app.modules.budget.entity.StatutBudget;
import com.app.modules.budget.repository.BudgetAnnuelRepository;
import com.app.modules.rapports.dto.ExportBudgetRequest;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.entity.TypeExport;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportBudgetService {

    private final PdfBuilderService pdfBuilderService;
    private final ExcelBuilderService excelBuilderService;
    private final ExportMinioService exportMinioService;
    private final ExportJobService exportJobService;
    private final ConfigExportRepository configExportRepository;
    private final AuditLogService auditLogService;
    private final BudgetAnnuelRepository budgetAnnuelRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public ExportJobResponse exporterBudgetPdf(ExportBudgetRequest req, UUID orgId, UUID userId) {
        int seuil = configExportRepository.findByOrganisationId(orgId).map(c -> c.getSeuilLignesSyncPdf()).orElse(500);
        List<Map<String, Object>> lines = loadExecutionBudget(orgId, req.annee());
        if (lines.size() <= 1) throw BusinessException.badRequest("EXPORT_DONNEES_ABSENTES");
        if (lines.size() - 1 <= seuil) {
            return generatePdfSync(req, orgId, userId, lines);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("annee", req.annee());
        return exportJobService.creerJob(orgId, userId, TypeExport.BUDGET_PREVISIONNEL_PDF, params);
    }

    @Transactional(readOnly = true)
    public ExportJobResponse exporterBudgetExcel(ExportBudgetRequest req, UUID orgId, UUID userId) {
        int seuil = configExportRepository.findByOrganisationId(orgId).map(c -> c.getSeuilLignesSyncExcel()).orElse(5000);
        List<Map<String, Object>> lines = loadExecutionBudget(orgId, req.annee());
        if (lines.size() <= 1) throw BusinessException.badRequest("EXPORT_DONNEES_ABSENTES");
        if (lines.size() - 1 <= seuil) {
            return generateExcelSync(req, orgId, userId, lines);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("annee", req.annee());
        return exportJobService.creerJob(orgId, userId, TypeExport.BUDGET_PREVISIONNEL_EXCEL, params);
    }

    @Async("exportExecutor")
    public void generateAsync(UUID jobId, TypeExport type, Map<String, Object> params, UUID orgId, UUID userId) {
        try {
            exportJobService.mettreAJourProgression(jobId, 10);
            Integer annee = (Integer) params.get("annee");
            if (annee == null) throw BusinessException.badRequest("EXPORT_PERIODE_INVALIDE");
            ExportBudgetRequest req = new ExportBudgetRequest(annee);
            List<Map<String, Object>> lines = loadExecutionBudget(orgId, annee);
            ExportJobResponse r =
                    (type == TypeExport.BUDGET_PREVISIONNEL_PDF)
                            ? generatePdfSync(req, orgId, userId, lines)
                            : generateExcelSync(req, orgId, userId, lines);
            exportJobService.marquerTermine(jobId, r.fichierUrl(), r.nomFichier(), r.tailleOctets(), r.nbLignes());
        } catch (Exception e) {
            log.error("Async BUDGET failed jobId={}", jobId, e);
            exportJobService.marquerErreur(jobId, e.getMessage());
        }
    }

    private ExportJobResponse generatePdfSync(ExportBudgetRequest req, UUID orgId, UUID userId, List<Map<String, Object>> lines) {
        String titre = "BUDGET " + req.annee() + " — EXÉCUTION BUDGÉTAIRE";
        PdfDocument pdfDoc = pdfBuilderService.creerDocument(orgId, titre);
        Document doc = new Document(pdfDoc);
        doc.setMargins(80, 30, 40, 30);

        var budget = budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(orgId, req.annee(), StatutBudget.VALIDE).orElse(null);
        doc.add(new Paragraph("Au " + LocalDate.now() + (budget != null && budget.getStatut() != null ? " — " + budget.getStatut().name() : "")).setItalic());

        pdfBuilderService.ajouterSection(doc, "Synthèse globale");
        BigDecimal depPrev = sum(lines, "type", "DEPENSE", "montant_prevu");
        BigDecimal depReal = sum(lines, "type", "DEPENSE", "montant_realise");
        BigDecimal recPrev = sum(lines, "type", "RECETTE", "montant_prevu");
        BigDecimal recReal = sum(lines, "type", "RECETTE", "montant_realise");

        Table synth = new Table(new float[] {25, 25, 25, 25}).useAllAvailableWidth();
        synth.addCell(h(""));
        synth.addCell(h("DÉPENSES"));
        synth.addCell(h("RECETTES"));
        synth.addCell(h("SOLDE"));

        BigDecimal solPrev = recPrev.subtract(depPrev);
        BigDecimal solReal = recReal.subtract(depReal);
        BigDecimal solEcart = solReal.subtract(solPrev);

        synth.addCell(k("Prévu"));
        synth.addCell(vMoney(depPrev));
        synth.addCell(vMoney(recPrev));
        synth.addCell(vMoney(solPrev));

        synth.addCell(k("Réalisé"));
        synth.addCell(vMoney(depReal));
        synth.addCell(vMoney(recReal));
        synth.addCell(vMoney(solReal));

        synth.addCell(k("Écart"));
        synth.addCell(vMoney(depReal.subtract(depPrev)));
        synth.addCell(vMoney(recReal.subtract(recPrev)));
        synth.addCell(vMoney(solEcart));

        doc.add(synth);

        pdfBuilderService.ajouterSection(doc, "Dépenses par catégorie");
        doc.add(buildBudgetTable(lines, "DEPENSE"));

        pdfBuilderService.ajouterSection(doc, "Recettes par catégorie");
        doc.add(buildBudgetTable(lines, "RECETTE"));

        byte[] bytes = pdfBuilderService.finaliser(pdfDoc, doc);
        String objectName = orgId + "/exports/budget/pdf/" + req.annee() + "-" + System.currentTimeMillis() + ".pdf";
        try {
            exportMinioService.uploadBytes(objectName, bytes, "application/pdf");
            String url = exportMinioService.presignGet(objectName, 48 * 3600);
            auditLogService.log(orgId, userId, "EXPORT", "Budget", null, null, Map.of("type", TypeExport.BUDGET_PREVISIONNEL_PDF.name()));
            return new ExportJobResponse(
                    null,
                    TypeExport.BUDGET_PREVISIONNEL_PDF.name(),
                    "TERMINE",
                    100,
                    url,
                    "budget-" + req.annee() + ".pdf",
                    (long) bytes.length,
                    Math.max(0, lines.size() - 1),
                    null,
                    null,
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            throw new BusinessException("EXPORT_MINIO_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Erreur lors de l'upload MinIO.");
        }
    }

    private ExportJobResponse generateExcelSync(ExportBudgetRequest req, UUID orgId, UUID userId, List<Map<String, Object>> lines) {
        Workbook wb = excelBuilderService.creerClasseur("Exécution");
        Sheet sheet = wb.getSheetAt(0);

        String[] headers = {"Catégorie", "Type", "Prévu EUR", "Réalisé EUR", "Écart EUR", "% exécution"};
        int[] widths = {28 * 256, 12 * 256, 14 * 256, 14 * 256, 14 * 256, 12 * 256};
        excelBuilderService.creerLigneEntete(sheet, 0, headers, widths);

        CellStyle data = excelBuilderService.getStyleDonnees();
        CellStyle money = excelBuilderService.getStyleMontant();

        int r = 1;
        for (int i = 1; i < lines.size(); i++) {
            Map<String, Object> line = lines.get(i);
            Row rr = sheet.createRow(r++);
            int c = 0;
            text(rr, c++, (String) line.get("categorie"), data);
            text(rr, c++, (String) line.get("type"), data);
            num(rr, c++, (BigDecimal) line.get("montant_prevu"), money);
            num(rr, c++, (BigDecimal) line.get("montant_realise"), money);
            num(rr, c++, (BigDecimal) line.get("ecart"), money);
            text(rr, c++, pct((BigDecimal) line.get("taux_execution_pct")), data);
        }

        excelBuilderService.ajouterOngletInfos(wb, orgId, TypeExport.BUDGET_PREVISIONNEL_EXCEL.name(), String.valueOf(req.annee()), Math.max(0, lines.size() - 1));
        byte[] bytes = excelBuilderService.finaliser(wb);

        String objectName = orgId + "/exports/budget/excel/" + req.annee() + "-" + System.currentTimeMillis() + ".xlsx";
        try {
            exportMinioService.uploadBytes(objectName, bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String url = exportMinioService.presignGet(objectName, 48 * 3600);
            auditLogService.log(orgId, userId, "EXPORT", "Budget", null, null, Map.of("type", TypeExport.BUDGET_PREVISIONNEL_EXCEL.name()));
            return new ExportJobResponse(
                    null,
                    TypeExport.BUDGET_PREVISIONNEL_EXCEL.name(),
                    "TERMINE",
                    100,
                    url,
                    "budget-" + req.annee() + ".xlsx",
                    (long) bytes.length,
                    Math.max(0, lines.size() - 1),
                    null,
                    null,
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            throw new BusinessException("EXPORT_MINIO_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Erreur lors de l'upload MinIO.");
        }
    }

    private List<Map<String, Object>> loadExecutionBudget(UUID orgId, int annee) {
        String sql =
                """
                SELECT v.categorie_libelle, v.type::text, v.montant_prevu,
                       v.montant_realise, v.ecart, v.taux_execution_pct
                FROM v_execution_budget v
                INNER JOIN budgets_annuels b ON b.id = v.budget_id
                WHERE b.organisation_id = ? AND b.annee = ? AND b.statut = 'VALIDE'
                ORDER BY v.categorie_libelle, v.type
                """;
        List<Map<String, Object>> data =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) ->
                                Map.<String, Object>of(
                                        "categorie", rs.getString(1),
                                        "type", rs.getString(2),
                                        "montant_prevu", rs.getBigDecimal(3),
                                        "montant_realise", rs.getBigDecimal(4),
                                        "ecart", rs.getBigDecimal(5),
                                        "taux_execution_pct", rs.getBigDecimal(6)),
                        orgId,
                        annee);
        // index 0 reserved (header sentinel)
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        out.add(Map.of());
        out.addAll(data);
        return out;
    }

    private Table buildBudgetTable(List<Map<String, Object>> lines, String type) {
        Table t =
                pdfBuilderService.creerTableau(
                        new String[] {"Catégorie", "Prévu (€)", "Réalisé (€)", "Écart (€)", "% exécution", "Statut"},
                        new float[] {30, 14, 14, 14, 12, 16});
        for (int i = 1; i < lines.size(); i++) {
            Map<String, Object> l = lines.get(i);
            if (!type.equals(String.valueOf(l.get("type")))) continue;
            BigDecimal prev = (BigDecimal) l.get("montant_prevu");
            BigDecimal real = (BigDecimal) l.get("montant_realise");
            BigDecimal pct = (BigDecimal) l.get("taux_execution_pct");
            String statut;
            if (pct != null && pct.compareTo(new BigDecimal("100")) > 0) {
                statut = "⚠ DÉPASSEMENT";
            } else if (pct != null && pct.compareTo(new BigDecimal("80")) >= 0) {
                statut = "⚡ ATTENTION";
            } else {
                statut = "✓ Normal";
            }
            t.addCell(new Cell().add(new Paragraph(String.valueOf(l.get("categorie")))).setPadding(4));
            t.addCell(new Cell().add(new Paragraph(money(prev))).setTextAlignment(TextAlignment.RIGHT).setPadding(4));
            t.addCell(new Cell().add(new Paragraph(money(real))).setTextAlignment(TextAlignment.RIGHT).setPadding(4));
            t.addCell(new Cell().add(new Paragraph(money((BigDecimal) l.get("ecart")))).setTextAlignment(TextAlignment.RIGHT).setPadding(4));
            t.addCell(new Cell().add(new Paragraph(pct(pct))).setPadding(4));
            t.addCell(new Cell().add(new Paragraph(statut)).setPadding(4));
        }
        return t;
    }

    private static BigDecimal sum(List<Map<String, Object>> lines, String key, String equals, String sumKey) {
        BigDecimal out = BigDecimal.ZERO;
        for (int i = 1; i < lines.size(); i++) {
            Map<String, Object> l = lines.get(i);
            if (!equals.equals(String.valueOf(l.get(key)))) continue;
            BigDecimal v = (BigDecimal) l.get(sumKey);
            if (v != null) out = out.add(v);
        }
        return out;
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String pct(BigDecimal v) {
        if (v == null) return "";
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static Cell h(String s) {
        return new Cell().add(new Paragraph(s).setBold()).setPadding(6).setTextAlignment(TextAlignment.CENTER);
    }

    private static Cell k(String s) {
        return new Cell().add(new Paragraph(s).setBold()).setPadding(4);
    }

    private static Cell vMoney(BigDecimal v) {
        return new Cell().add(new Paragraph(money(v) + " €")).setPadding(4).setTextAlignment(TextAlignment.RIGHT);
    }

    private static void text(Row r, int idx, String val, CellStyle style) {
        var c = r.createCell(idx);
        c.setCellValue(val == null ? "" : val);
        c.setCellStyle(style);
    }

    private static void num(Row r, int idx, BigDecimal val, CellStyle style) {
        var c = r.createCell(idx);
        c.setCellValue(val == null ? 0 : val.doubleValue());
        c.setCellStyle(style);
    }
}

