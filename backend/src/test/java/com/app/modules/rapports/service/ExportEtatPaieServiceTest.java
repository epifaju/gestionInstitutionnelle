package com.app.modules.rapports.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rapports.dto.ExportEtatPaieRequest;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportEtatPaieServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b1111111-1111-4111-8111-111111111101");

    @Mock
    private PdfBuilderService pdfBuilderService;
    @Mock
    private ExportMinioService exportMinioService;
    @Mock
    private ExportJobService exportJobService;
    @Mock
    private ConfigExportRepository configExportRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private OrganisationRepository organisationRepository;

    @Test
    void exporterEtatPaiePdf_quandAucuneDonnee_lanceException() {
        ExcelBuilderService excel = new ExcelBuilderService(organisationRepository);
        ExportEtatPaieService svc =
                new ExportEtatPaieService(
                        pdfBuilderService,
                        excel,
                        exportMinioService,
                        exportJobService,
                        configExportRepository,
                        auditLogService,
                        jdbcTemplate);

        when(configExportRepository.findByOrganisationId(orgId)).thenReturn(Optional.empty());
        // countEtatPaie service=null path
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> svc.exporterEtatPaiePdf(new ExportEtatPaieRequest(2026, 3, null), orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_DONNEES_ABSENTES");
    }

    @Test
    void exporterEtatPaieExcel_filtreParService_reduitNbLignes() throws Exception {
        Organisation org = new Organisation();
        org.setId(orgId);
        org.setNom("Org");
        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));

        ExcelBuilderService excel = new ExcelBuilderService(organisationRepository);
        ExportEtatPaieService svc =
                new ExportEtatPaieService(
                        pdfBuilderService,
                        excel,
                        exportMinioService,
                        exportJobService,
                        configExportRepository,
                        auditLogService,
                        jdbcTemplate);

        when(configExportRepository.findByOrganisationId(orgId)).thenReturn(Optional.empty());
        // service not null path
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any())).thenReturn(3L);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                Map.of("matricule", "M1", "nom_prenom", "A A", "service", "Comptabilité", "poste", "X", "montant", new java.math.BigDecimal("1.0"), "devise", "EUR", "mode_paiement", "", "date_paiement", "", "statut", "PAYE"),
                                Map.of("matricule", "M2", "nom_prenom", "B B", "service", "Comptabilité", "poste", "X", "montant", new java.math.BigDecimal("1.0"), "devise", "EUR", "mode_paiement", "", "date_paiement", "", "statut", "PAYE"),
                                Map.of("matricule", "M3", "nom_prenom", "C C", "service", "Comptabilité", "poste", "X", "montant", new java.math.BigDecimal("1.0"), "devise", "EUR", "mode_paiement", "", "date_paiement", "", "statut", "PAYE")));

        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("http://signed");

        ExportJobResponse out = svc.exporterEtatPaieExcel(new ExportEtatPaieRequest(2026, 3, "Comptabilité"), orgId, userId);
        assertThat(out.nbLignes()).isEqualTo(3);
        assertThat(out.statut()).isEqualTo("TERMINE");
        assertThat(out.fichierUrl()).isNotBlank();
    }
}

