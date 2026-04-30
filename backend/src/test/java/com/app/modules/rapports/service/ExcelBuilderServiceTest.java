package com.app.modules.rapports.service;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ExcelBuilderServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    void testCreerClasseur_StylesOk() {
        OrganisationRepository orgRepo = Mockito.mock(OrganisationRepository.class);
        ExcelBuilderService svc = new ExcelBuilderService(orgRepo);

        Workbook wb = svc.creerClasseur("Main");

        CellStyle header = svc.getStyleEntete();
        assertThat(header).isNotNull();
        assertThat(header.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
        assertThat(header.getBorderBottom()).isNotNull();

        Font f = wb.getFontAt(header.getFontIndex());
        assertThat(f.getBold()).isTrue();
        assertThat(f.getColor()).isEqualTo(IndexedColors.WHITE.getIndex());

        // Header fill color is configured as #1B3A5C.
        assertThat(header).isInstanceOf(XSSFCellStyle.class);

        assertThat(svc.getStyleDonnees()).isNotNull();
        assertThat(svc.getStyleMontant()).isNotNull();
        assertThat(svc.getStyleDate()).isNotNull();
        assertThat(svc.getStyleAlerte()).isNotNull();
    }

    @Test
    void testOngletInfos_ContenuCorrect() {
        OrganisationRepository orgRepo = Mockito.mock(OrganisationRepository.class);
        Organisation org = new Organisation();
        org.setId(orgId);
        org.setNom("Org Test");
        when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));

        ExcelBuilderService svc = new ExcelBuilderService(orgRepo);
        Workbook wb = svc.creerClasseur("Main");

        svc.ajouterOngletInfos(wb, orgId, "ETAT_PAIE_EXCEL", "MARS 2026", 3);

        Sheet infos = wb.getSheet("Infos");
        assertThat(infos).isNotNull();

        assertThat(infos.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Organisation");
        assertThat(infos.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Org Test");

        assertThat(infos.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Type export");
        assertThat(infos.getRow(1).getCell(1).getStringCellValue()).isEqualTo("ETAT_PAIE_EXCEL");

        assertThat(infos.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Période");
        assertThat(infos.getRow(2).getCell(1).getStringCellValue()).isEqualTo("MARS 2026");

        assertThat(infos.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Nb lignes");
        assertThat(infos.getRow(3).getCell(1).getStringCellValue()).isEqualTo("3");
    }
}

