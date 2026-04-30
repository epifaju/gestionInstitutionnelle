package com.app.modules.rapports.service;

import com.app.modules.auth.repository.OrganisationRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ExcelBuilderService {

    private final OrganisationRepository organisationRepository;

    private CellStyle styleEntete;
    private CellStyle styleDonnees;
    private CellStyle styleMontant;
    private CellStyle styleDate;
    private CellStyle styleAlerte;

    public ExcelBuilderService(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    public Workbook creerClasseur(String nomOngletPrincipal) {
        XSSFWorkbook wb = new XSSFWorkbook();
        initStyles(wb);
        wb.createSheet(nomOngletPrincipal == null || nomOngletPrincipal.isBlank() ? "Export" : nomOngletPrincipal);
        return wb;
    }

    public CellStyle getStyleEntete() {
        return styleEntete;
    }

    public CellStyle getStyleDonnees() {
        return styleDonnees;
    }

    public CellStyle getStyleMontant() {
        return styleMontant;
    }

    public CellStyle getStyleDate() {
        return styleDate;
    }

    public CellStyle getStyleAlerte() {
        return styleAlerte;
    }

    public Row creerLigneEntete(Sheet sheet, int rowNum, String[] titres, int[] largeurs) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < titres.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(titres[i] == null ? "" : titres[i]);
            c.setCellStyle(styleEntete);
            if (largeurs != null && i < largeurs.length && largeurs[i] > 0) {
                sheet.setColumnWidth(i, largeurs[i]);
            } else {
                sheet.setColumnWidth(i, 20 * 256);
            }
        }
        return row;
    }

    public void ajouterOngletInfos(Workbook wb, UUID orgId, String typeExport, String periode, int nbLignes) {
        Sheet sheet = wb.createSheet("Infos");
        int r = 0;

        String orgName = organisationRepository.findById(orgId).map(o -> o.getNom()).orElse("Organisation");

        r = kv(sheet, r, "Organisation", orgName);
        r = kv(sheet, r, "Type export", typeExport == null ? "" : typeExport);
        r = kv(sheet, r, "Période", periode == null ? "" : periode);
        r = kv(sheet, r, "Nb lignes", String.valueOf(nbLignes));
        r = kv(sheet, r, "Généré le", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    public byte[] finaliser(Workbook wb) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            wb.close();
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private int kv(Sheet s, int row, String k, String v) {
        Row r = s.createRow(row);
        Cell c0 = r.createCell(0);
        c0.setCellValue(k);
        c0.setCellStyle(styleEntete);
        Cell c1 = r.createCell(1);
        c1.setCellValue(v == null ? "" : v);
        c1.setCellStyle(styleDonnees);
        return row + 1;
    }

    private void initStyles(XSSFWorkbook wb) {
        DataFormat fmt = wb.createDataFormat();

        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        XSSFCellStyle header = wb.createCellStyle();
        header.setFillForegroundColor(new XSSFColor(new byte[] {0x1B, 0x3A, 0x5C}, null));
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setFont(headerFont);
        header.setBorderBottom(BorderStyle.THIN);
        header.setBorderTop(BorderStyle.THIN);
        header.setBorderLeft(BorderStyle.THIN);
        header.setBorderRight(BorderStyle.THIN);
        this.styleEntete = header;

        XSSFCellStyle data = wb.createCellStyle();
        data.setBorderBottom(BorderStyle.THIN);
        data.setBorderTop(BorderStyle.THIN);
        data.setBorderLeft(BorderStyle.THIN);
        data.setBorderRight(BorderStyle.THIN);
        this.styleDonnees = data;

        XSSFCellStyle money = wb.createCellStyle();
        money.cloneStyleFrom(data);
        money.setDataFormat(fmt.getFormat("#,##0.00"));
        this.styleMontant = money;

        XSSFCellStyle date = wb.createCellStyle();
        date.cloneStyleFrom(data);
        date.setDataFormat(fmt.getFormat("dd/MM/yyyy"));
        this.styleDate = date;

        XSSFCellStyle alert = wb.createCellStyle();
        alert.cloneStyleFrom(data);
        alert.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xFF, (byte) 0xE0, (byte) 0xE0}, null));
        alert.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        this.styleAlerte = alert;
    }
}

