package com.app.modules.rapports.service;

import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.finance.dto.StatsResponse;
import com.app.modules.finance.service.StatsService;
import com.app.modules.inventaire.dto.StatsInventaireResponse;
import com.app.modules.inventaire.service.InventaireService;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RapportServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private StatsService statsService;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private InventaireService inventaireService;

    @InjectMocks private RapportService rapportService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Test
    void exportCsv_entiteInvalide_refuse() {
        assertThatThrownBy(() -> rapportService.exportCsv(orgId, "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_ENTITE_INVALIDE");
    }

    @Test
    void exportCsv_factures_contientBOM_etHeader_memeSansLignes() {
        // query does nothing => only header remains
        doReturn(null).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), eq(orgId));

        byte[] csv = rapportService.exportCsv(orgId, "factures");
        String s = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(s).startsWith("\uFEFFreference;fournisseur;date_facture;montant_ttc_eur;statut;categorie\n");
    }

    @Test
    void getDashboard_employe_pasDAggregats_finance_inventaire() {
        // For EMPLOYE: should not call statsService or inventaireService
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(orgId), eq(userId))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId), eq(userId)))
                .thenReturn(List.of());

        var dash = rapportService.getDashboard(orgId, userId, Role.EMPLOYE);

        assertThat(dash.kpis().totalDepenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dash.kpis().totalRecettes()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dash.kpis().solde()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dash.alertesBudget()).isEmpty();
        assertThat(dash.top5Fournisseurs()).isEmpty();
        verify(statsService, never()).getStatsMensuelles(any(), anyInt(), anyInt());
        verify(inventaireService, never()).getStats(any());
    }

    @Test
    void exportBilanMensuelPdf_retourneBytesNonVides() {
        StatsResponse stats =
                new StatsResponse(
                        2026,
                        4,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "EUR",
                        0L,
                        0L,
                        BigDecimal.ZERO,
                        List.of(),
                        List.of(),
                        List.of());
        when(statsService.getStatsMensuelles(orgId, 2026, 4)).thenReturn(stats);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(orgId))).thenReturn(0L);

        byte[] pdf = rapportService.exportBilanMensuelPdf(orgId, 2026, 4, "Org");

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(200);
    }

    @Test
    void exportBilanAnnuelExcel_retourneXlsxZip() {
        StatsResponse st =
                new StatsResponse(
                        2026,
                        1,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "EUR",
                        0L,
                        0L,
                        BigDecimal.ZERO,
                        List.of(),
                        List.of(),
                        List.of());
        when(statsService.getStatsMensuelles(eq(orgId), eq(2026), anyInt())).thenReturn(st);
        doReturn(null).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class));

        byte[] xlsx = rapportService.exportBilanAnnuelExcel(orgId, 2026);
        assertThat(xlsx).isNotNull();
        assertThat(xlsx.length).isGreaterThan(200);
        // XLSX is a ZIP => "PK"
        assertThat((char) xlsx[0]).isEqualTo('P');
        assertThat((char) xlsx[1]).isEqualTo('K');
    }

    @Test
    void getDashboard_nonEmploye_appelleStatsEtInventaire() {
        Organisation o = new Organisation();
        o.setAlerteBudgetPct(80);
        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(o));

        StatsResponse st =
                new StatsResponse(
                        LocalDate.now().getYear(),
                        LocalDate.now().getMonthValue(),
                        new BigDecimal("10"),
                        new BigDecimal("20"),
                        new BigDecimal("10"),
                        "EUR",
                        0L,
                        0L,
                        BigDecimal.ZERO,
                        List.of(),
                        List.of(),
                        List.of());
        when(statsService.getStatsMensuelles(eq(orgId), anyInt(), anyInt())).thenReturn(st);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId), anyInt(), any(BigDecimal.class)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId), anyInt()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(orgId))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId)))
                .thenReturn(List.of());

        StatsInventaireResponse inv = new StatsInventaireResponse(BigDecimal.ZERO, List.of(), List.of());
        when(inventaireService.getStats(orgId)).thenReturn(inv);

        rapportService.getDashboard(orgId, userId, Role.ADMIN);

        verify(statsService, atLeastOnce()).getStatsMensuelles(eq(orgId), anyInt(), anyInt());
        verify(inventaireService).getStats(orgId);
    }
}

