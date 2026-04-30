package com.app.modules.rapports.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.missions.entity.FraisMission;
import com.app.modules.missions.entity.Mission;
import com.app.modules.missions.entity.StatutFrais;
import com.app.modules.missions.entity.StatutMission;
import com.app.modules.missions.repository.FraisMissionRepository;
import com.app.modules.missions.repository.MissionRepository;
import com.app.modules.rapports.dto.ExportEtatPaieRequest;
import com.app.modules.rapports.dto.ExportJournalAuditRequest;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.entity.ExportJob;
import com.app.modules.rapports.entity.StatutExportJob;
import com.app.modules.rapports.entity.TypeExport;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.modules.rapports.repository.ExportJobRepository;
import com.app.shared.exception.BusinessException;
import com.app.config.MinioProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ExportNotefraisServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b1111111-1111-4111-8111-111111111101");
    private static final UUID missionId = UUID.fromString("c2222222-2222-4222-8222-222222222201");

    @Mock
    private ConfigExportRepository configExportRepository;
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MissionRepository missionRepository;
    @Mock
    private FraisMissionRepository fraisMissionRepository;
    @Mock
    private ExportMinioService exportMinioService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    // ExportJobService dependencies
    @Mock
    private ExportJobRepository exportJobRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    // ExportEtatPaieService deps
    @Mock
    private JdbcTemplate jdbcTemplate;

    // ExportJournalAuditService deps
    @Mock
    private com.app.audit.AuditLogRepository auditLogRepository;
    @Mock
    private com.app.audit.AuditLogService auditLogService2;

    private PdfBuilderService pdfBuilder() {
        MinioProperties props = new MinioProperties();
        props.setBucket("bucket");
        Organisation org = new Organisation();
        org.setId(orgId);
        org.setNom("Org Test");
        lenient().when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));
        // no config export => defaults in PdfBuilderService; no logo => no minio read
        return new PdfBuilderService(configExportRepository, organisationRepository, minioClient, props);
    }

    private Mission mission() {
        Mission m = new Mission();
        m.setId(missionId);
        m.setOrganisationId(orgId);
        m.setTitre("Mission Test");
        m.setDestination("Paris");
        m.setPaysDestination("France");
        m.setStatut(StatutMission.TERMINEE);
        m.setDateDepart(LocalDate.of(2026, 3, 1));
        m.setDateRetour(LocalDate.of(2026, 3, 3));
        m.setAvanceVersee(null);
        m.setAvanceDevise("EUR");
        return m;
    }

    private FraisMission frais(LocalDate d, String type, String desc, String justificatifUrl) {
        FraisMission f = new FraisMission();
        f.setId(UUID.randomUUID());
        f.setDateFrais(d);
        f.setTypeFrais(type);
        f.setDescription(desc);
        f.setMontant(new java.math.BigDecimal("10.00"));
        f.setDevise("EUR");
        f.setTauxChangeEur(new java.math.BigDecimal("1.0"));
        f.setStatut(StatutFrais.VALIDE);
        f.setJustificatifUrl(justificatifUrl);
        return f;
    }

    private ExportJobService exportJobService() {
        MinioProperties props = new MinioProperties();
        props.setBucket("bucket");
        return new ExportJobService(exportJobRepository, publisher, minioClient, props);
    }

    @Test
    void testExportNoteFrais_Synchrone_PdfGenere() throws Exception {
        PdfBuilderService pdfBuilder = pdfBuilder();
        ExportNotefraisService service =
                new ExportNotefraisService(
                        pdfBuilder,
                        missionRepository,
                        fraisMissionRepository,
                        exportMinioService,
                        exportJobService(),
                        auditLogService,
                        utilisateurRepository);

        Mission m = mission();
        List<FraisMission> frais =
                List.of(
                        frais(LocalDate.of(2026, 3, 1), "Transport", "Train", "https://minio/x1.pdf"),
                        frais(LocalDate.of(2026, 3, 2), "Hébergement", "Hotel", ""),
                        frais(LocalDate.of(2026, 3, 3), "Repas", "Déjeuner", "https://minio/x2.pdf"));

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(m));
        when(fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(missionId)).thenReturn(frais);
        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("https://presigned/test.pdf");

        ArgumentCaptor<byte[]> bytesCap = ArgumentCaptor.forClass(byte[].class);
        ExportJobResponse out = service.exporterNoteFrais(missionId, orgId, userId);

        assertThat(out.statut()).isEqualTo("TERMINE");
        assertThat(out.fichierUrl()).isNotNull();

        verify(exportMinioService).uploadBytes(anyString(), bytesCap.capture(), eq("application/pdf"));
        byte[] pdfBytes = bytesCap.getValue();
        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1000);

        verify(auditLogService).log(eq(orgId), eq(userId), eq("EXPORT"), eq("NoteFrais"), eq(missionId), eq(null), any(Map.class));
    }

    @Test
    void testExportNoteFrais_MissionInconnue_LanceException() {
        ExportNotefraisService service =
                new ExportNotefraisService(
                        pdfBuilder(),
                        missionRepository,
                        fraisMissionRepository,
                        exportMinioService,
                        exportJobService(),
                        auditLogService,
                        utilisateurRepository);

        UUID unknown = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
        when(missionRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exporterNoteFrais(unknown, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_NOT_FOUND");
    }

    @Test
    void testExportEtatPaie_FiltrageParService() throws Exception {
        // Arrange a service with a real ExcelBuilder but mocked JDBC and MinIO.
        Organisation org = new Organisation();
        org.setId(orgId);
        org.setNom("Org Test");
        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));

        ExcelBuilderService excel = new ExcelBuilderService(organisationRepository);
        ExportJobService jobSvc = exportJobService();
        ExportEtatPaieService service =
                new ExportEtatPaieService(
                        pdfBuilder(),
                        excel,
                        exportMinioService,
                        jobSvc,
                        configExportRepository,
                        auditLogService,
                        jdbcTemplate);

        ExportEtatPaieRequest req = new ExportEtatPaieRequest(2026, 3, "Comptabilité");

        when(configExportRepository.findByOrganisationId(orgId)).thenReturn(Optional.empty());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any())).thenReturn(3L);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                Map.of("matricule", "M1", "nom_prenom", "A A", "service", "Comptabilité", "poste", "X", "montant", new java.math.BigDecimal("1.0"), "devise", "EUR", "mode_paiement", "", "date_paiement", "", "statut", "PAYE"),
                                Map.of("matricule", "M2", "nom_prenom", "B B", "service", "Comptabilité", "poste", "X", "montant", new java.math.BigDecimal("1.0"), "devise", "EUR", "mode_paiement", "", "date_paiement", "", "statut", "PAYE"),
                                Map.of("matricule", "M3", "nom_prenom", "C C", "service", "Comptabilité", "poste", "X", "montant", new java.math.BigDecimal("1.0"), "devise", "EUR", "mode_paiement", "", "date_paiement", "", "statut", "PAYE")));

        when(exportMinioService.presignGet(anyString(), anyInt())).thenReturn("https://presigned/etat.xlsx");

        ExportJobResponse out = service.exporterEtatPaieExcel(req, orgId, userId);
        assertThat(out.nbLignes()).isEqualTo(3);
    }

    @Test
    void testExportJournal_PeriodeTropLarge_LanceException() {
        ExportJournalAuditService service =
                new ExportJournalAuditService(
                        pdfBuilder(),
                        new ExcelBuilderService(organisationRepository),
                        exportMinioService,
                        exportJobService(),
                        configExportRepository,
                        auditLogRepository,
                        auditLogService2);

        ExportJournalAuditRequest req =
                new ExportJournalAuditRequest(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2027, 3, 1),
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> service.exporterJournalPdf(req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PERIODE_TROP_LARGE");
    }

    @Test
    void testExportJob_DeuxJobsMemeType_LanceException() {
        ExportJobService svc = exportJobService();
        when(exportJobRepository.existsByOrganisationIdAndTypeExportAndStatutIn(eq(orgId), eq(TypeExport.NOTE_FRAIS_PDF), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> svc.creerJob(orgId, userId, TypeExport.NOTE_FRAIS_PDF, Map.of("missionId", missionId)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_JOB_EN_COURS");
    }

    @Test
    void testNettoyageJobsExpires() throws Exception {
        // Given a finished job already expired, with an object name tracked.
        ExportJobService svc = exportJobService();

        ExportJob j = new ExportJob();
        j.setId(UUID.fromString("d3333333-3333-4333-8333-333333333301"));
        j.setOrganisationId(orgId);
        j.setDemandePar(userId);
        j.setTypeExport(TypeExport.NOTE_FRAIS_PDF);
        j.setStatut(StatutExportJob.TERMINE);
        j.setProgression(100);
        j.setFichierUrl("https://presigned/old.pdf");
        j.setNomFichier("old.pdf");
        j.setTailleOctets(1234L);
        j.setNbLignes(3);
        Map<String, Object> p = new HashMap<>();
        p.put("_objectName", "org/exports/x.pdf");
        j.setParametres(p);

        when(exportJobRepository.findByStatutAndExpireABefore(eq(StatutExportJob.TERMINE), any(Instant.class)))
                .thenReturn(List.of(j));
        when(exportJobRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.nettoyerJobsExpires();

        // then
        assertThat(j.getStatut()).isEqualTo(StatutExportJob.EXPIRE);
        assertThat(j.getFichierUrl()).isNull();
        verify(minioClient).removeObject(any(io.minio.RemoveObjectArgs.class));
    }
}

