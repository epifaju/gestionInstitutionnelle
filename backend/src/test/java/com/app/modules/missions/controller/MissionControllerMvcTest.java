package com.app.modules.missions.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.missions.dto.FraisRequest;
import com.app.modules.missions.dto.FraisResponse;
import com.app.modules.missions.dto.MissionRequest;
import com.app.modules.missions.dto.MissionResponse;
import com.app.modules.missions.service.MissionService;
import com.app.modules.rh.dto.SalarieResponse;
import com.app.modules.rh.service.SalarieService;
import com.app.modules.workflow.service.WorkflowEngineService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.app.shared.storage.MinioStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissionControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MissionService missionService = mock(MissionService.class);
    private final SalarieService salarieService = mock(SalarieService.class);
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final WorkflowEngineService workflowEngineService = mock(WorkflowEngineService.class);

    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SALARIE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID MISSION_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID FRAIS_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new MissionController(
                                        missionService, salarieService, minioStorageService, workflowEngineService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(
                                new MappingJackson2HttpMessageConverter(objectMapper),
                                new ResourceHttpMessageConverter())
                        .setValidator(validator)
                        .setCustomArgumentResolvers(
                                new AuthenticationPrincipalArgumentResolver(),
                                new PageableHandlerMethodArgumentResolver())
                        .build();

        when(workflowEngineService.isEnabled(any(), any())).thenReturn(false);
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

    private static MissionResponse sampleMission() {
        return new MissionResponse(
                MISSION_ID,
                "Mission X",
                "Paris",
                "FR",
                "Audit",
                LocalDate.of(2026, 4, 24),
                LocalDate.of(2026, 4, 26),
                3,
                "BROUILLON",
                "Doe Jane",
                new BigDecimal("100.00"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                List.of(),
                LocalDateTime.of(2026, 1, 10, 10, 0));
    }

    private static FraisResponse sampleFrais() {
        return new FraisResponse(
                FRAIS_ID,
                "REPAS",
                "Déjeuner",
                LocalDate.of(2026, 4, 25),
                new BigDecimal("12.00"),
                "EUR",
                new BigDecimal("12.00"),
                null,
                "SOUMIS");
    }

    private static SalarieResponse sampleSalarie() {
        return new SalarieResponse(
                SALARIE_ID,
                "EMP-1",
                "Doe",
                "Jane",
                "user@test.com",
                null,
                null,
                null,
                null,
                null,
                "ACTIF",
                null,
                null,
                null,
                null,
                LocalDateTime.now());
    }

    private static MissionRequest missionReq() {
        return new MissionRequest(
                "Mission X",
                "Paris",
                "FR",
                "Audit",
                LocalDate.of(2026, 4, 24),
                LocalDate.of(2026, 4, 26),
                new BigDecimal("100.00"),
                "EUR");
    }

    @Test
    void list_rh_ok() throws Exception {
        when(missionService.list(eq(ORG_ID), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleMission()), PageRequest.of(0, 20), 1));

        withAuth(auth(Role.RH), () -> mockMvc.perform(get("/api/v1/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].titre").value("Mission X")));
    }

    @Test
    void list_employe_filtreSurLui() throws Exception {
        when(salarieService.getMe(ORG_ID, USER_ID, "user@test.com")).thenReturn(sampleSalarie());
        when(missionService.list(eq(ORG_ID), any(), eq(SALARIE_ID), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleMission()), PageRequest.of(0, 20), 1));

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/missions"))
                .andExpect(status().isOk()));

        verify(missionService).list(eq(ORG_ID), any(), eq(SALARIE_ID), any(), any(), any());
    }

    @Test
    void create_rh_exigeSalarie() throws Exception {
        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missionReq())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SALARIE_REQUIS")));
    }

    @Test
    void create_rh_avecSalarie_ok() throws Exception {
        when(missionService.creer(any(MissionRequest.class), eq(ORG_ID), eq(SALARIE_ID)))
                .thenReturn(sampleMission());

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/missions")
                        .param("salarieId", SALARIE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missionReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(MISSION_ID.toString())));
    }

    @Test
    void create_employe_ok() throws Exception {
        when(salarieService.getMe(ORG_ID, USER_ID, "user@test.com")).thenReturn(sampleSalarie());
        when(missionService.creer(any(MissionRequest.class), eq(ORG_ID), eq(SALARIE_ID)))
                .thenReturn(sampleMission());

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missionReq())))
                .andExpect(status().isOk()));
    }

    @Test
    void get_ok() throws Exception {
        when(missionService.getById(MISSION_ID, ORG_ID)).thenReturn(sampleMission());

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/missions/{id}", MISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.destination").value("Paris")));
    }

    @Test
    void update_ok() throws Exception {
        when(salarieService.getMe(ORG_ID, USER_ID, "user@test.com")).thenReturn(sampleSalarie());
        when(missionService.update(eq(MISSION_ID), any(), eq(ORG_ID), eq(SALARIE_ID)))
                .thenReturn(sampleMission());

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(put("/api/v1/missions/{id}", MISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missionReq())))
                .andExpect(status().isOk()));
    }

    @Test
    void soumettre_ok() throws Exception {
        when(salarieService.getMe(ORG_ID, USER_ID, "user@test.com")).thenReturn(sampleSalarie());
        when(missionService.soumettre(MISSION_ID, ORG_ID, SALARIE_ID)).thenReturn(sampleMission());

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(post("/api/v1/missions/{id}/soumettre", MISSION_ID))
                .andExpect(status().isOk()));
    }

    @Test
    void approuver_ok() throws Exception {
        when(missionService.approuver(eq(MISSION_ID), eq(USER_ID), any(), eq(ORG_ID)))
                .thenReturn(sampleMission());

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/missions/{id}/approuver", MISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avanceVersee\":50}"))
                .andExpect(status().isOk()));

        verify(missionService).approuver(MISSION_ID, USER_ID, new BigDecimal("50"), ORG_ID);
    }

    @Test
    void refuser_ok() throws Exception {
        when(missionService.refuser(MISSION_ID, "Non", ORG_ID)).thenReturn(sampleMission());

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(post("/api/v1/missions/{id}/refuser", MISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motifRefus\":\"Non\"}"))
                .andExpect(status().isOk()));
    }

    @Test
    void terminer_ok() throws Exception {
        when(missionService.terminer(MISSION_ID, ORG_ID)).thenReturn(sampleMission());

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/missions/{id}/terminer", MISSION_ID))
                .andExpect(status().isOk()));
    }

    @Test
    void uploadOrdre_ok() throws Exception {
        when(missionService.uploadOrdreMission(eq(MISSION_ID), any(), eq(ORG_ID)))
                .thenReturn("/api/v1/missions/" + MISSION_ID + "/ordre-mission");

        MockMultipartFile file =
                new MockMultipartFile("file", "ordre.pdf", "application/pdf", "%PDF-1.4\n".getBytes());

        withAuth(auth(Role.RH), () -> mockMvc.perform(multipart("/api/v1/missions/{id}/ordre-mission", MISSION_ID).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("/api/v1/missions/" + MISSION_ID + "/ordre-mission")));
    }

    @Test
    void ajouterFrais_ok() throws Exception {
        when(missionService.ajouterFrais(eq(MISSION_ID), any(FraisRequest.class), any(), eq(ORG_ID)))
                .thenReturn(sampleFrais());

        FraisRequest fr = new FraisRequest(
                "REPAS", "Déjeuner", LocalDate.of(2026, 4, 25), new BigDecimal("12.00"), "EUR");
        MockMultipartFile meta =
                new MockMultipartFile(
                        "req", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(fr));

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(multipart("/api/v1/missions/{id}/frais", MISSION_ID).file(meta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeFrais").value("REPAS")));
    }

    @Test
    void validerFrais_ok() throws Exception {
        when(missionService.validerFrais(MISSION_ID, FRAIS_ID, ORG_ID)).thenReturn(sampleFrais());

        withAuth(auth(Role.FINANCIER), () ->
                mockMvc.perform(post("/api/v1/missions/{id}/frais/{fraisId}/valider", MISSION_ID, FRAIS_ID))
                        .andExpect(status().isOk()));
    }

    @Test
    void rembourserFrais_ok() throws Exception {
        when(missionService.rembourserFrais(MISSION_ID, FRAIS_ID, ORG_ID, USER_ID)).thenReturn(sampleFrais());

        withAuth(auth(Role.FINANCIER), () ->
                mockMvc.perform(post("/api/v1/missions/{id}/frais/{fraisId}/rembourser", MISSION_ID, FRAIS_ID))
                        .andExpect(status().isOk()));
    }

    @Test
    void downloadOrdre_ok() throws Exception {
        String objectName = "missions/" + ORG_ID + "/" + MISSION_ID + "/ordre/o.pdf";
        when(missionService.getOrdreObjectName(MISSION_ID, ORG_ID)).thenReturn(objectName);
        when(minioStorageService.download(objectName))
                .thenReturn(new MinioStorageService.Download(
                        objectName, "application/pdf", 4, new ByteArrayInputStream("%PDF".getBytes())));

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/missions/{id}/ordre-mission", MISSION_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("o.pdf"))));
    }
}
