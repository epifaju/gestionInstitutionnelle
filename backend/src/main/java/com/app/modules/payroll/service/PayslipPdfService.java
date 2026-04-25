package com.app.modules.payroll.service;

import com.app.modules.payroll.entity.BulletinLigne;
import com.app.modules.payroll.entity.BulletinPaie;
import com.app.modules.payroll.entity.PayrollEmployerSettings;
import com.app.modules.rh.entity.Salarie;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Génération PDF "bulletin de paie" à partir du snapshot (bulletin + lignes).
 * Le contenu détaillé est piloté par le paramétrage (rubriques/cotisations).
 */
public class PayslipPdfService {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] renderPdf(PayrollEmployerSettings employer, BulletinPaie b, List<BulletinLigne> lignes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        Salarie s = b.getSalarie();

        doc.add(new Paragraph("BULLETIN DE PAIE")
                .setTextAlignment(TextAlignment.CENTER)
                .setBold()
                .setFontSize(14));
        doc.add(new Paragraph("Période : " + pad2(b.getMois()) + "/" + b.getAnnee()
                + "  •  Date de paiement : " + (b.getDatePaiement() != null ? DF.format(b.getDatePaiement()) : "—"))
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(10));

        doc.add(new Paragraph(" "));

        // Employer / Employee blocks
        Table top = new Table(new float[]{1, 1}).useAllAvailableWidth();
        top.addCell(blockCell("Employeur", employerBlock(employer)));
        top.addCell(blockCell("Salarié", employeeBlock(s, b)));
        doc.add(top);

        doc.add(new Paragraph(" "));
        doc.add(new LineSeparator(new SolidLine()));
        doc.add(new Paragraph(" "));

        // Sections: REMUNERATION / COTISATIONS / IMPOT / NET
        addSection(doc, "Rémunération", b, lignes, "REMUNERATION");
        addSection(doc, "Cotisations et contributions", b, lignes, "COTISATIONS");
        addSection(doc, "Impôt sur le revenu", b, lignes, "IMPOT");
        addSection(doc, "Net", b, lignes, "NET");

        doc.add(new Paragraph(" "));

        // Totaux / synthèse
        doc.add(new Paragraph("Synthèse").setBold());
        Table sum = new Table(new float[]{2.5f, 1f}).useAllAvailableWidth();
        sum.addCell(k("Brut"));
        sum.addCell(v(money(b.getBrut(), b.getDevise())));
        sum.addCell(k("Total cotisations salariales"));
        sum.addCell(v(money(b.getTotalCotSal(), b.getDevise())));
        sum.addCell(k("Total cotisations patronales"));
        sum.addCell(v(money(b.getTotalCotPat(), b.getDevise())));
        sum.addCell(k("Net imposable"));
        sum.addCell(v(money(b.getNetImposable(), b.getDevise())));
        sum.addCell(k("Prélèvement à la source"));
        sum.addCell(v(money(b.getPasMontant(), b.getDevise()) + (b.getPasTaux() != null ? " (" + pct(b.getPasTaux()) + ")" : "")));
        sum.addCell(k("Net à payer"));
        sum.addCell(v(money(b.getNetAPayer(), b.getDevise())));
        doc.add(sum);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Document généré automatiquement. Les paramètres (rubriques/cotisations, plafonds, profils) sont administrables.")
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER));

        doc.close();
        return out.toByteArray();
    }

    private static Cell blockCell(String title, String content) {
        return new Cell()
                .add(new Paragraph(title).setBold())
                .add(new Paragraph(content).setFontSize(9))
                .setPadding(8);
    }

    private static void addSection(Document doc, String title, BulletinPaie b, List<BulletinLigne> lignes, String section) {
        List<BulletinLigne> filtered = lignes.stream().filter(x -> Objects.equals(section, x.getSection())).toList();
        if (filtered.isEmpty()) return;

        doc.add(new Paragraph(title).setBold());
        Table t = new Table(new float[]{2.3f, 1f, 1f, 1f, 1f}).useAllAvailableWidth();
        t.addHeaderCell(h("Libellé"));
        t.addHeaderCell(h("Base"));
        t.addHeaderCell(h("Taux sal."));
        t.addHeaderCell(h("Montant sal."));
        t.addHeaderCell(h("Montant pat."));

        BigDecimal subSal = BigDecimal.ZERO;
        BigDecimal subPat = BigDecimal.ZERO;

        for (BulletinLigne l : filtered) {
            t.addCell(c(l.getLibelle()));
            t.addCell(r(money(l.getBase(), b.getDevise())));
            t.addCell(r(pct(l.getTauxSalarial())));
            t.addCell(r(money(l.getMontantSalarial(), b.getDevise())));
            t.addCell(r(money(l.getMontantPatronal(), b.getDevise())));
            subSal = subSal.add(nz(l.getMontantSalarial()));
            subPat = subPat.add(nz(l.getMontantPatronal()));
        }

        // Sous-total
        Border topBorder = new SolidBorder(1);
        Cell st = new Cell(1, 3).add(new Paragraph("Sous-total").setBold().setFontSize(9)).setBorderTop(topBorder);
        t.addCell(st);
        t.addCell(r(money(subSal, b.getDevise())).setBorderTop(topBorder));
        t.addCell(r(money(subPat, b.getDevise())).setBorderTop(topBorder));

        doc.add(t);
        doc.add(new Paragraph(" "));
    }

    private static String employerBlock(PayrollEmployerSettings e) {
        if (e == null) {
            return "Paramètres employeur non configurés.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(nz(e.getRaisonSociale())).append("\n");
        if (e.getAdresseLigne1() != null) sb.append(e.getAdresseLigne1()).append("\n");
        if (e.getAdresseLigne2() != null) sb.append(e.getAdresseLigne2()).append("\n");
        if (e.getCodePostal() != null || e.getVille() != null) sb.append(nz(e.getCodePostal())).append(" ").append(nz(e.getVille())).append("\n");
        if (e.getPays() != null) sb.append(e.getPays()).append("\n");
        if (e.getSiret() != null) sb.append("SIRET: ").append(e.getSiret()).append("\n");
        if (e.getNaf() != null) sb.append("NAF: ").append(e.getNaf()).append("\n");
        if (e.getConventionLibelle() != null) sb.append("Convention: ").append(e.getConventionLibelle()).append("\n");
        return sb.toString().trim();
    }

    private static String employeeBlock(Salarie s, BulletinPaie b) {
        StringBuilder sb = new StringBuilder();
        sb.append(nz(s.getNom())).append(" ").append(nz(s.getPrenom())).append("\n");
        sb.append("Matricule: ").append(nz(s.getMatricule())).append("\n");
        sb.append("Cadre: ").append(b.isCadre() ? "Oui" : "Non").append("\n");
        if (b.getConventionLibelle() != null) sb.append("Convention: ").append(b.getConventionLibelle()).append("\n");
        return sb.toString().trim();
    }

    private static Cell h(String s) {
        return new Cell().add(new Paragraph(s).setBold().setFontSize(9));
    }

    private static Cell c(String s) {
        return new Cell().add(new Paragraph(s == null ? "" : s).setFontSize(9));
    }

    private static Cell r(String s) {
        return new Cell().add(new Paragraph(s == null ? "" : s).setFontSize(9)).setTextAlignment(TextAlignment.RIGHT);
    }

    private static Cell k(String s) {
        return new Cell().add(new Paragraph(s).setFontSize(9)).setBorder(Border.NO_BORDER);
    }

    private static Cell v(String s) {
        return new Cell().add(new Paragraph(s == null ? "" : s).setFontSize(9)).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER);
    }

    private static String money(BigDecimal v, String devise) {
        if (v == null) return "";
        return v.setScale(2, RoundingMode.HALF_UP) + " " + (devise == null ? "EUR" : devise);
    }

    private static String pct(BigDecimal v) {
        if (v == null) return "";
        return v.multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP) + " %";
    }

    private static String pad2(Integer m) {
        if (m == null) return "--";
        return m < 10 ? "0" + m : String.valueOf(m);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

