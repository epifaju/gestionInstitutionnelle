package com.app.modules.rapports.service;

import com.app.audit.AuditLogService;
import com.app.modules.rapports.dto.ExportEtatPaieRequest;
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
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportEtatPaieService {

    private final PdfBuilderService pdfBuilderService;
    private final ExcelBuilderService excelBuilderService;
    private final ExportMinioService exportMinioService;
    private final ExportJobService exportJobService;
    private final ConfigExportRepository configExportRepository;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public ExportJobResponse exporterEtatPaiePdf(ExportEtatPaieRequest req, UUID orgId, UUID userId) {
        int seuil = configExportRepository.findByOrganisationId(orgId).map(c -> c.getSeuilLignesSyncPdf()).orElse(500);
        long count = countEtatPaie(req, orgId);
        if (count == 0) throw BusinessException.badRequest("EXPORT_DONNEES_ABSENTES");
        if (count <= seuil) {
            return generatePdfSync(req, orgId, userId);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("annee", req.annee());
        params.put("mois", req.mois());
        params.put("service", req.service());
        return exportJobService.creerJob(orgId, userId, TypeExport.ETAT_PAIE_PDF, params);
    }

    @Transactional(readOnly = true)
    public ExportJobResponse exporterEtatPaieExcel(ExportEtatPaieRequest req, UUID orgId, UUID userId) {
        int seuil = configExportRepository.findByOrganisationId(orgId).map(c -> c.getSeuilLignesSyncExcel()).orElse(5000);
        long count = countEtatPaie(req, orgId);
        if (count == 0) throw BusinessException.badRequest("EXPORT_DONNEES_ABSENTES");
        if (count <= seuil) {
            return generateExcelSync(req, orgId, userId);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("annee", req.annee());
        params.put("mois", req.mois());
        params.put("service", req.service());
        return exportJobService.creerJob(orgId, userId, TypeExport.ETAT_PAIE_EXCEL, params);
    }

    @Async("exportExecutor")
    public void generateAsync(UUID jobId, TypeExport type, Map<String, Object> params, UUID orgId, UUID userId) {
        try {
            exportJobService.mettreAJourProgression(jobId, 10);
            ExportEtatPaieRequest req =
                    new ExportEtatPaieRequest(
                            (Integer) params.get("annee"),
                            (Integer) params.get("mois"),
                            (String) params.get("service"));

            if (type == TypeExport.ETAT_PAIE_PDF) {
                ExportJobResponse r = generatePdfSync(req, orgId, userId);
                exportJobService.setObjectName(jobId, (String) ((Map<?, ?>) params).get("_objectName"));
                exportJobService.marquerTermine(jobId, r.fichierUrl(), r.nomFichier(), r.tailleOctets(), r.nbLignes());
            } else if (type == TypeExport.ETAT_PAIE_EXCEL) {
                ExportJobResponse r = generateExcelSync(req, orgId, userId);
                exportJobService.setObjectName(jobId, (String) ((Map<?, ?>) params).get("_objectName"));
                exportJobService.marquerTermine(jobId, r.fichierUrl(), r.nomFichier(), r.tailleOctets(), r.nbLignes());
            } else {
                exportJobService.marquerErreur(jobId, "Type export non supporté");
            }
        } catch (Exception e) {
            log.error("Async ETAT_PAIE failed jobId={}", jobId, e);
            exportJobService.marquerErreur(jobId, e.getMessage());
        }
    }

    private long countEtatPaie(ExportEtatPaieRequest req, UUID orgId) {
        if (req.service() == null || req.service().isBlank()) {
            String sql =
                    """
                    SELECT COUNT(*)
                    FROM paiements_salaires p
                    INNER JOIN salaries s ON s.id = p.salarie_id
                    WHERE p.organisation_id = ? AND p.annee = ? AND p.mois = ?
                    """;
            Long c = jdbcTemplate.queryForObject(sql, Long.class, orgId, req.annee(), req.mois());
            return c == null ? 0 : c;
        }

        String sql =
                """
                SELECT COUNT(*)
                FROM paiements_salaires p
                INNER JOIN salaries s ON s.id = p.salarie_id
                WHERE p.organisation_id = ? AND p.annee = ? AND p.mois = ?
                  AND s.service = ?
                """;
        Long c =
                jdbcTemplate.queryForObject(
                        sql, Long.class, orgId, req.annee(), req.mois(), req.service());
        return c == null ? 0 : c;
    }

    @Transactional
    protected ExportJobResponse generatePdfSync(ExportEtatPaieRequest req, UUID orgId, UUID userId) {
        List<Map<String, Object>> rows = loadEtatPaie(req, orgId);

        String moisAnnee = labelMois(req.annee(), req.mois());
        PdfDocument pdfDoc = pdfBuilderService.creerDocument(orgId, "ÉTAT DE PAIE — " + moisAnnee);
        Document doc = new Document(pdfDoc);
        doc.setMargins(80, 30, 40, 30);

        pdfBuilderService.ajouterSection(doc, "Récapitulatif");
        Table recap = new Table(new float[] {35, 65}).useAllAvailableWidth();
        recap.addCell(k("Période"));
        recap.addCell(v(moisAnnee));
        recap.addCell(k("Service"));
        recap.addCell(v(req.service() == null ? "Tous" : req.service()));

        long nb = rows.size();
        BigDecimal total = rows.stream().map(r -> (BigDecimal) r.get("montant")).reduce(BigDecimal.ZERO, BigDecimal::add);
        long paye = rows.stream().filter(r -> "PAYE".equals(String.valueOf(r.get("statut")))).count();
        long attente = rows.stream().filter(r -> "EN_ATTENTE".equals(String.valueOf(r.get("statut")))).count();
        long annule = rows.stream().filter(r -> "ANNULE".equals(String.valueOf(r.get("statut")))).count();

        recap.addCell(k("Nb salariés"));
        recap.addCell(v(String.valueOf(nb)));
        recap.addCell(k("Total net"));
        recap.addCell(v(total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " €"));
        recap.addCell(k("Payés"));
        recap.addCell(v(String.valueOf(paye)));
        recap.addCell(k("En attente"));
        recap.addCell(v(String.valueOf(attente)));
        recap.addCell(k("Annulés"));
        recap.addCell(v(String.valueOf(annule)));
        doc.add(recap);

        pdfBuilderService.ajouterSection(doc, "Détail par salarié");
        Table t =
                pdfBuilderService.creerTableau(
                        new String[] {"Matricule", "Nom", "Service", "Poste", "Net (€)", "Mode paiement", "Date paiement", "Statut"},
                        new float[] {12, 18, 14, 18, 10, 12, 10, 6});
        for (Map<String, Object> r : rows) {
            t.addCell(c(String.valueOf(r.get("matricule"))));
            t.addCell(c(String.valueOf(r.get("nom_prenom"))));
            t.addCell(c(String.valueOf(r.get("service"))));
            t.addCell(c(String.valueOf(r.get("poste"))));
            t.addCell(c(String.valueOf(r.get("montant"))).setTextAlignment(TextAlignment.RIGHT));
            t.addCell(c(String.valueOf(r.get("mode_paiement"))));
            t.addCell(c(String.valueOf(r.get("date_paiement"))));
            t.addCell(c(String.valueOf(r.get("statut"))));
        }
        doc.add(t);

        byte[] bytes = pdfBuilderService.finaliser(pdfDoc, doc);
        String objectName = orgId + "/exports/etats-paie/pdf/" + req.annee() + "-" + req.mois() + "-" + System.currentTimeMillis() + ".pdf";
        try {
            exportMinioService.uploadBytes(objectName, bytes, "application/pdf");
            String url = exportMinioService.presignGet(objectName, 48 * 3600);
            auditLogService.log(orgId, userId, "EXPORT", "EtatPaie", null, null, Map.of("type", TypeExport.ETAT_PAIE_PDF.name()));
            return new ExportJobResponse(
                    null,
                    TypeExport.ETAT_PAIE_PDF.name(),
                    "TERMINE",
                    100,
                    url,
                    "etat-paie-" + req.annee() + "-" + req.mois() + ".pdf",
                    (long) bytes.length,
                    rows.size(),
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
    protected ExportJobResponse generateExcelSync(ExportEtatPaieRequest req, UUID orgId, UUID userId) {
        List<Map<String, Object>> rows = loadEtatPaie(req, orgId);
        Workbook wb = excelBuilderService.creerClasseur("Détail");
        Sheet detail = wb.getSheetAt(0);

        String[] headers = {"Matricule", "Nom", "Service", "Poste", "Net", "Devise", "Mode paiement", "Date paiement", "Statut"};
        int[] widths = {14 * 256, 22 * 256, 18 * 256, 20 * 256, 12 * 256, 8 * 256, 16 * 256, 14 * 256, 12 * 256};
        excelBuilderService.creerLigneEntete(detail, 0, headers, widths);

        CellStyle data = excelBuilderService.getStyleDonnees();
        CellStyle money = excelBuilderService.getStyleMontant();
        int r = 1;
        for (Map<String, Object> row : rows) {
            Row rr = detail.createRow(r++);
            int c = 0;
            cell(rr, c++, String.valueOf(row.get("matricule")), data);
            cell(rr, c++, String.valueOf(row.get("nom_prenom")), data);
            cell(rr, c++, String.valueOf(row.get("service")), data);
            cell(rr, c++, String.valueOf(row.get("poste")), data);
            org.apache.poi.ss.usermodel.Cell cm = rr.createCell(c++);
            cm.setCellValue(((BigDecimal) row.get("montant")).doubleValue());
            cm.setCellStyle(money);
            cell(rr, c++, String.valueOf(row.get("devise")), data);
            cell(rr, c++, String.valueOf(row.get("mode_paiement")), data);
            cell(rr, c++, String.valueOf(row.get("date_paiement")), data);
            cell(rr, c++, String.valueOf(row.get("statut")), data);
        }

        String periode = labelMois(req.annee(), req.mois()) + (req.service() != null ? " — " + req.service() : "");
        excelBuilderService.ajouterOngletInfos(wb, orgId, TypeExport.ETAT_PAIE_EXCEL.name(), periode, rows.size());
        byte[] bytes = excelBuilderService.finaliser(wb);

        String objectName = orgId + "/exports/etats-paie/excel/" + req.annee() + "-" + req.mois() + "-" + System.currentTimeMillis() + ".xlsx";
        try {
            exportMinioService.uploadBytes(objectName, bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String url = exportMinioService.presignGet(objectName, 48 * 3600);
            auditLogService.log(orgId, userId, "EXPORT", "EtatPaie", null, null, Map.of("type", TypeExport.ETAT_PAIE_EXCEL.name()));
            return new ExportJobResponse(
                    null,
                    TypeExport.ETAT_PAIE_EXCEL.name(),
                    "TERMINE",
                    100,
                    url,
                    "etat-paie-" + req.annee() + "-" + req.mois() + ".xlsx",
                    (long) bytes.length,
                    rows.size(),
                    null,
                    null,
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            throw new BusinessException("EXPORT_MINIO_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Erreur lors de l'upload MinIO.");
        }
    }

    private List<Map<String, Object>> loadEtatPaie(ExportEtatPaieRequest req, UUID orgId) {
        boolean hasService = req.service() != null && !req.service().isBlank();
        String sql =
                """
                SELECT s.matricule,
                       (s.nom || ' ' || s.prenom) AS nom_prenom,
                       s.service,
                       s.poste,
                       p.montant,
                       p.devise,
                       COALESCE(p.mode_paiement, '') AS mode_paiement,
                       COALESCE(p.date_paiement::text, '') AS date_paiement,
                       p.statut::text AS statut
                FROM paiements_salaires p
                INNER JOIN salaries s ON s.id = p.salarie_id
                WHERE p.organisation_id = ? AND p.annee = ? AND p.mois = ?
                """
                + (hasService ? "  AND s.service = ?\n" : "")
                + """
                ORDER BY s.service ASC, s.nom ASC, s.prenom ASC
                """;
        if (hasService) {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("matricule", nvl(rs.getString("matricule")));
                        m.put("nom_prenom", nvl(rs.getString("nom_prenom")));
                        m.put("service", nvl(rs.getString("service")));
                        m.put("poste", nvl(rs.getString("poste")));
                        m.put("montant", rs.getBigDecimal("montant"));
                        m.put("devise", nvl(rs.getString("devise")));
                        m.put("mode_paiement", nvl(rs.getString("mode_paiement")));
                        m.put("date_paiement", nvl(rs.getString("date_paiement")));
                        m.put("statut", nvl(rs.getString("statut")));
                        return m;
                    },
                    orgId,
                    req.annee(),
                    req.mois(),
                    req.service());
        }

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("matricule", nvl(rs.getString("matricule")));
                    m.put("nom_prenom", nvl(rs.getString("nom_prenom")));
                    m.put("service", nvl(rs.getString("service")));
                    m.put("poste", nvl(rs.getString("poste")));
                    m.put("montant", rs.getBigDecimal("montant"));
                    m.put("devise", nvl(rs.getString("devise")));
                    m.put("mode_paiement", nvl(rs.getString("mode_paiement")));
                    m.put("date_paiement", nvl(rs.getString("date_paiement")));
                    m.put("statut", nvl(rs.getString("statut")));
                    return m;
                },
                orgId,
                req.annee(),
                req.mois());
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String labelMois(int annee, int mois) {
        var ym = YearMonth.of(annee, mois);
        return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.FRANCE).toUpperCase(Locale.FRANCE) + " " + annee;
    }

    private static Cell k(String s) {
        return new Cell().add(new Paragraph(s).setBold()).setPadding(4);
    }

    private static Cell v(String s) {
        return new Cell().add(new Paragraph(s == null ? "" : s)).setPadding(4);
    }

    private static Cell c(String s) {
        return new Cell().add(new Paragraph(s == null ? "" : s)).setPadding(4);
    }

    private static void cell(Row r, int idx, String val, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell c = r.createCell(idx);
        c.setCellValue(val == null ? "" : val);
        c.setCellStyle(style);
    }
}

