package com.app.modules.rapports.service;

import com.app.config.MinioProperties;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rapports.entity.ConfigExport;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Service
public class PdfBuilderService {

    private static final DeviceRgb DEFAULT_PRIMARY = new DeviceRgb(0x1B, 0x3A, 0x5C);
    private static final DeviceRgb ROW_ALT = new DeviceRgb(0xF9, 0xFA, 0xFB);

    private final ConfigExportRepository configExportRepository;
    private final OrganisationRepository organisationRepository;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    private final Map<PdfDocument, ByteArrayOutputStream> outputs =
            Collections.synchronizedMap(new WeakHashMap<>());

    public PdfBuilderService(
            ConfigExportRepository configExportRepository,
            OrganisationRepository organisationRepository,
            @Qualifier("internalMinioClient") MinioClient minioClient,
            MinioProperties minioProperties
    ) {
        this.configExportRepository = configExportRepository;
        this.organisationRepository = organisationRepository;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public PdfDocument creerDocument(UUID orgId, String titrePrincipal) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        pdfDoc.setDefaultPageSize(PageSize.A4);
        outputs.put(pdfDoc, baos);

        ConfigExport cfg =
                configExportRepository
                        .findByOrganisationId(orgId)
                        .orElseGet(
                                () -> {
                                    ConfigExport c = new ConfigExport();
                                    c.setOrganisationId(orgId);
                                    c.setPiedPageMention("Document confidentiel — usage interne");
                                    c.setCouleurPrincipale("#1B3A5C");
                                    c.setSeuilLignesSyncPdf(500);
                                    c.setSeuilLignesSyncExcel(5000);
                                    c.setWatermarkActif(false);
                                    c.setWatermarkTexte("CONFIDENTIEL");
                                    return c;
                                });

        String orgName =
                organisationRepository
                        .findById(orgId)
                        .map(o -> o.getNom() != null ? o.getNom() : "Organisation")
                        .orElse("Organisation");

        DeviceRgb primary = parseColorOrDefault(cfg.getCouleurPrincipale(), DEFAULT_PRIMARY);
        String logoObjectName = firstNonBlank(cfg.getLogoUrl(),
                organisationRepository.findById(orgId).map(o -> o.getLogoUrl()).orElse(null));
        Image logo = tryLoadLogo(logoObjectName);

        pdfDoc.addEventHandler(
                PdfDocumentEvent.START_PAGE,
                new HeaderFooterHandler(
                        primary,
                        cfg.getPiedPageMention() != null ? cfg.getPiedPageMention() : "Document confidentiel — usage interne",
                        orgName,
                        titrePrincipal,
                        logo,
                        Boolean.TRUE.equals(cfg.getWatermarkActif()),
                        cfg.getWatermarkTexte() != null ? cfg.getWatermarkTexte() : "CONFIDENTIEL"));

        // Keep a reference to the stream through the writer; finaliser() will close it.
        pdfDoc.getWriter().setCloseStream(false);
        return pdfDoc;
    }

    public Table creerTableau(String[] entetes, float[] largeurs) {
        if (entetes == null || largeurs == null || entetes.length != largeurs.length) {
            throw BusinessException.badRequest("EXPORT_TABLE_INVALID");
        }
        Table table = new Table(UnitValue.createPercentArray(largeurs)).useAllAvailableWidth();
        table.setMarginTop(8);

        for (String h : entetes) {
            Cell c =
                    new Cell()
                            .add(new Paragraph(h == null ? "" : h).setBold().setFontColor(ColorConstants.WHITE))
                            .setBackgroundColor(DEFAULT_PRIMARY)
                            .setBorder(Border.NO_BORDER)
                            .setPadding(6);
            table.addHeaderCell(c);
        }
        return table;
    }

    public void ajouterSection(Document doc, String titre) {
        DeviceRgb primary = DEFAULT_PRIMARY;
        Paragraph p =
                new Paragraph(titre == null ? "" : titre)
                        .setBold()
                        .setFontSize(12)
                        .setFontColor(primary)
                        .setBackgroundColor(new DeviceRgb(0xF3, 0xF4, 0xF6))
                        .setPadding(6)
                        .setMarginTop(12)
                        .setMarginBottom(6);
        doc.add(p);
    }

    public void ajouterPieceJointe(Document doc, String libelle, String minioUrl) {
        String label = libelle == null ? "" : libelle;
        String url = minioUrl == null ? "" : minioUrl;
        String nomFichier = extractFilename(url);
        Paragraph p = new Paragraph("📎 " + label + " — " + nomFichier);
        if (!url.isBlank()) {
            p.setFontColor(new DeviceRgb(0x16, 0x5D, 0xFF));
            p.setUnderline();
        }
        doc.add(p.setMarginBottom(2));
    }

    public byte[] finaliser(PdfDocument pdfDoc, Document doc) {
        try {
            if (doc != null) {
                doc.close();
            } else if (pdfDoc != null) {
                pdfDoc.close();
            }
            if (pdfDoc == null) return new byte[0];
            ByteArrayOutputStream os = outputs.get(pdfDoc);
            if (os == null) {
                return new byte[0];
            }
            return os.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("EXPORT_PDF_BUILD_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Impossible de générer le PDF.");
        } finally {
            if (pdfDoc != null) {
                outputs.remove(pdfDoc);
            }
        }
    }

    private Image tryLoadLogo(String objectNameOrUrl) {
        if (objectNameOrUrl == null || objectNameOrUrl.isBlank()) return null;
        if (objectNameOrUrl.startsWith("http://") || objectNameOrUrl.startsWith("https://")) {
            return null;
        }
        try (InputStream in =
                minioClient.getObject(
                        GetObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectNameOrUrl).build())) {
            byte[] bytes = in.readAllBytes();
            Image img = new Image(ImageDataFactory.create(bytes));
            img.setAutoScale(true);
            img.setMaxHeight(40);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractFilename(String urlOrObject) {
        if (urlOrObject == null || urlOrObject.isBlank()) return "";
        String s = urlOrObject;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash < s.length() - 1) return s.substring(slash + 1);
        return s;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static DeviceRgb parseColorOrDefault(String hex, DeviceRgb fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        String v = hex.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() != 6) return fallback;
        try {
            int r = Integer.parseInt(v.substring(0, 2), 16);
            int g = Integer.parseInt(v.substring(2, 4), 16);
            int b = Integer.parseInt(v.substring(4, 6), 16);
            return new DeviceRgb(r, g, b);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class HeaderFooterHandler implements IEventHandler {
        private final DeviceRgb primary;
        private final String piedPageMention;
        private final String orgName;
        private final String title;
        private final Image logo;
        private final boolean watermark;
        private final String watermarkText;
        private final DateTimeFormatter dt =
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE).withZone(ZoneId.systemDefault());

        private HeaderFooterHandler(
                DeviceRgb primary,
                String piedPageMention,
                String orgName,
                String title,
                Image logo,
                boolean watermark,
                String watermarkText) {
            this.primary = primary;
            this.piedPageMention = piedPageMention;
            this.orgName = orgName;
            this.title = title;
            this.logo = logo;
            this.watermark = watermark;
            this.watermarkText = watermarkText;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle ps = page.getPageSize();
            PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);

            // Header band
            float headerH = 60f;
            canvas.saveState();
            canvas.setFillColor(primary);
            canvas.rectangle(ps.getLeft(), ps.getTop() - headerH, ps.getWidth(), headerH);
            canvas.fill();
            canvas.restoreState();

            // Header text
            try {
                var font = PdfFontFactory.createFont();
                canvas.beginText();
                canvas.setFontAndSize(font, 10);
                canvas.setFillColor(ColorConstants.WHITE);
                canvas.moveText(ps.getLeft() + 70, ps.getTop() - 22);
                canvas.showText(orgName);
                canvas.endText();

                canvas.beginText();
                canvas.setFontAndSize(font, 12);
                canvas.setFillColor(ColorConstants.WHITE);
                canvas.moveText(ps.getLeft() + ps.getWidth() / 2 - (title.length() * 3f), ps.getTop() - 38);
                canvas.showText(title);
                canvas.endText();

                canvas.beginText();
                canvas.setFontAndSize(font, 9);
                canvas.setFillColor(ColorConstants.WHITE);
                canvas.moveText(ps.getRight() - 160, ps.getTop() - 22);
                canvas.showText("Généré le " + dt.format(Instant.now()));
                canvas.endText();
            } catch (IOException ignored) {
                // font fallback: ignore
            }

            // Logo (simple placement)
            if (logo != null) {
                try {
                    logo.setFixedPosition(ps.getLeft() + 10, ps.getTop() - 50);
                    new com.itextpdf.layout.Canvas(canvas, ps).add(logo);
                } catch (Exception ignored) {
                }
            }

            // Footer
            float footerY = ps.getBottom() + 20;
            try {
                var font = PdfFontFactory.createFont();
                canvas.beginText();
                canvas.setFontAndSize(font, 8);
                canvas.setFillColor(new DeviceRgb(0x11, 0x11, 0x11));
                canvas.moveText(ps.getLeft() + 20, footerY);
                canvas.showText(piedPageMention);
                canvas.endText();

                int pageNum = pdf.getPageNumber(page);
                int total = pdf.getNumberOfPages();
                canvas.beginText();
                canvas.setFontAndSize(font, 8);
                canvas.moveText(ps.getRight() - 80, footerY);
                canvas.showText("Page " + pageNum + " / " + total);
                canvas.endText();
            } catch (IOException ignored) {
            }

            // Watermark
            if (watermark && watermarkText != null && !watermarkText.isBlank()) {
                try {
                    var font = PdfFontFactory.createFont();
                    canvas.saveState();
                    canvas.setFillColor(new DeviceRgb(0x99, 0x99, 0x99));
                    canvas.beginText();
                    canvas.setFontAndSize(font, 60);
                    canvas.setTextMatrix((float) Math.cos(Math.toRadians(45)), (float) Math.sin(Math.toRadians(45)),
                            (float) -Math.sin(Math.toRadians(45)), (float) Math.cos(Math.toRadians(45)),
                            ps.getWidth() / 4, ps.getHeight() / 2);
                    canvas.showText(watermarkText);
                    canvas.endText();
                    canvas.restoreState();
                } catch (IOException ignored) {
                }
            }
        }
    }
}

