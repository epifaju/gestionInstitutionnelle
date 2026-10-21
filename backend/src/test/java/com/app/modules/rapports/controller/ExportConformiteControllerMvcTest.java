package com.app.modules.rapports.controller;

import com.app.audit.AuditLogRepository;
import com.app.audit.entity.AuditLog;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rapports.dto.ConfigExportRequest;
import com.app.modules.rapports.dto.ExportBudgetRequest;
import com.app.modules.rapports.dto.ExportEtatPaieRequest;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.dto.ExportJournalAuditRequest;
import com.app.modules.rapports.dto.ExportNotefraisRequest;
import com.app.modules.rapports.entity.ConfigExport;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.modules.rapports.service.ExportBudgetService;
import com.app.modules.rapports.service.ExportEtatPaieService;
import com.app.modules.rapports.service.ExportJobService;
import com.app.modules.rapports.service.ExportJournalAuditService;
import com.app.modules.rapports.service.ExportMinioService;
import com.app.modules.rapports.service.ExportNotefraisService;
import com.app.shared.dto.PageResponse;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExportConformiteControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final ExportNotefraisService exportNotefraisService = mock(ExportNotefraisService.class);
    private final ExportEtatPaieService exportEtatPaieService = mock(ExportEtatPaieService.class);
    private final ExportBudgetService exportBudgetService = mock(ExportBudgetService.class);
    private final ExportJournalAuditService exportJournalAuditService = mock(ExportJournalAuditService.class);
    private final ExportJobService exportJobService = mock(ExportJobService.class);
    private final ConfigExportRepository configExportRepository = mock(ConfigExportRepository.class);
    private final ExportMinioService exportMinioService = mock(ExportMinioService.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID MISSION_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ExportConformiteController(
                                        exportNotefraisService,
                                        exportEtatPaieService,
                                        exportBudgetService,
                                        exportJournalAuditService,
                                        exportJobService,
                                        configExportRepository,
                                        exportMinioService,
                                        auditLogRepository))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    private UsernamePasswordAuthenticationToken auth(Role role) {
        Organisation o = new Organisation();
        o.setId(ORG_ID);
        o.setNom("Org");
        Utilisateur u = new Utilisateur();
        u.setId(USER_ID);
        u.setOrganisationId(ORG_ID);
        u.setOrganisation(o);
        u.setEmail("user@test.com");
        u.setRole(role);
        CustomUserDetails cud = new CustomUserDetails(u);
        return new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
    }

    private void withAuth(UsernamePasswordAuthenticationToken auth, ThrowingRunnable r) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            r.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static ExportJobResponse syncJob(String type) {
        return new ExportJobResponse(
                null, type, "TERMINE", 100, "https://file", "out.pdf", 10L, 1, null, null, LocalDateTime.now());
    }

    private static ExportJobResponse asyncJob(String type) {
        return new ExportJobResponse(
                JOB_ID, type, "EN_ATTENTE", 0, null, null, null, null, null, null, LocalDateTime.now());
    }

    @Test
    void exportNoteFrais_sync_200() throws Exception {
        when(exportNotefraisService.exporterNoteFrais(MISSION_ID, ORG_ID, USER_ID))
                .thenReturn(syncJob("NOTE_FRAIS"));

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/exports/notes-frais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExportNotefraisRequest(MISSION_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("TERMINE")));
    }

    @Test
    void exportBudgetPdf_async_202() throws Exception {
        when(exportBudgetService.exporterBudgetPdf(any(ExportBudgetRequest.class), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(asyncJob("BUDGET_PREVISIONNEL_PDF"));

        withAuth(auth(Role.FINANCIER), () -> mockMvc.perform(post("/api/v1/exports/budget/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExportBudgetRequest(2026))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(JOB_ID.toString())));
    }

    @Test
    void exportBudgetExcel_ok() throws Exception {
        when(exportBudgetService.exporterBudgetExcel(any(), eq(ORG_ID), eq(USER_ID))).thenReturn(syncJob("BUDGET_XLS"));

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(post("/api/v1/exports/budget/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExportBudgetRequest(2026))))
                .andExpect(status().isOk()));
    }

    @Test
    void exportEtatPaiePdf_ok() throws Exception {
        when(exportEtatPaieService.exporterEtatPaiePdf(any(), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(syncJob("ETAT_PAIE_PDF"));

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/exports/etats-paie/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExportEtatPaieRequest(2026, 3, null))))
                .andExpect(status().isOk()));
    }

    @Test
    void exportEtatPaieExcel_ok() throws Exception {
        when(exportEtatPaieService.exporterEtatPaieExcel(any(), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(syncJob("ETAT_PAIE_XLS"));

        withAuth(auth(Role.FINANCIER), () -> mockMvc.perform(post("/api/v1/exports/etats-paie/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExportEtatPaieRequest(2026, 3, "RH"))))
                .andExpect(status().isOk()));
    }

    @Test
    void exportJournalAuditPdf_ok() throws Exception {
        when(exportJournalAuditService.exporterJournalPdf(any(), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(syncJob("JOURNAL_AUDIT_PDF"));

        ExportJournalAuditRequest req =
                new ExportJournalAuditRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null, null);

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(post("/api/v1/exports/journal-audit/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()));
    }

    @Test
    void exportJournalAuditExcel_ok() throws Exception {
        when(exportJournalAuditService.exporterJournalExcel(any(), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(syncJob("JOURNAL_AUDIT_EXCEL"));

        ExportJournalAuditRequest req =
                new ExportJournalAuditRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "Facture", "UPDATE", null);

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(post("/api/v1/exports/journal-audit/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()));
    }

    @Test
    void exportJournalAuditCsv_ok() throws Exception {
        AuditLog log = new AuditLog();
        log.setDateAction(Instant.parse("2026-01-15T10:00:00Z"));
        log.setUtilisateurId(USER_ID);
        log.setAction("UPDATE");
        log.setEntite("Facture");
        log.setEntiteId(JOB_ID);
        log.setIpAddress("127.0.0.1");

        when(auditLogRepository.countForExport(eq(ORG_ID), any(), any(), any(), any(), any())).thenReturn(1L);
        when(auditLogRepository.findForExport(eq(ORG_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 1000), 1));

        ExportJournalAuditRequest req =
                new ExportJournalAuditRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null, null);

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(post("/api/v1/exports/journal-audit/csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("journal-audit-")))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv"))));
    }

    @Test
    void exportJournalAuditCsv_periodeInvalide() throws Exception {
        ExportJournalAuditRequest req =
                new ExportJournalAuditRequest(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, null);

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(post("/api/v1/exports/journal-audit/csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXPORT_PERIODE_INVALIDE")));
    }

    @Test
    void listJobs_ok() throws Exception {
        when(exportJobService.listJobsOrg(eq(ORG_ID), eq(USER_ID), any()))
                .thenReturn(new PageResponse<>(List.of(asyncJob("X")), 0, 20, 1, 1, true));

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/exports/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(JOB_ID.toString())));
    }

    @Test
    void getJob_ok() throws Exception {
        when(exportJobService.getJob(JOB_ID, ORG_ID)).thenReturn(asyncJob("X"));

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/exports/jobs/{id}", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(JOB_ID.toString())));
    }

    @Test
    void deleteJob_ok() throws Exception {
        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(delete("/api/v1/exports/jobs/{id}", JOB_ID))
                .andExpect(status().isOk()));

        verify(exportJobService).annulerJob(JOB_ID, ORG_ID, USER_ID);
    }

    @Test
    void getConfig_creeSiAbsent() throws Exception {
        when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
        when(configExportRepository.save(any(ConfigExport.class))).thenAnswer(inv -> {
            ConfigExport c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(get("/api/v1/exports/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
    }

    @Test
    void updateConfig_ok() throws Exception {
        ConfigExport cfg = new ConfigExport();
        cfg.setId(UUID.randomUUID());
        cfg.setOrganisationId(ORG_ID);
        when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(cfg));
        when(configExportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConfigExportRequest req = new ConfigExportRequest("Mention", "#112233", 100, 200, true, "CONFIDENTIEL");

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(put("/api/v1/exports/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.piedPageMention").value("Mention")));
    }

    @Test
    void uploadLogo_ok() throws Exception {
        when(configExportRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
        when(configExportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(exportMinioService.presignGet(any(), anyInt())).thenReturn("https://logo");

        MockMultipartFile logo =
                new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1, 2, 3});

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(multipart("/api/v1/exports/config/logo").file(logo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("https://logo")));

        verify(exportMinioService).uploadBytes(any(), any(), eq("image/png"));
    }

    @Test
    void uploadLogo_refuseSiVide() throws Exception {
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[0]);

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(multipart("/api/v1/exports/config/logo").file(logo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXPORT_FICHIER_INVALIDE")));
    }
}
