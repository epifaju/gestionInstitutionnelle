package com.app.modules.rh.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.ConfigAlerteRequest;
import com.app.modules.rh.dto.ContratRequest;
import com.app.modules.rh.dto.ContratResponse;
import com.app.modules.rh.dto.DecisionFinCddRequest;
import com.app.modules.rh.dto.EcheanceDashboardResponse;
import com.app.modules.rh.dto.EcheanceRequest;
import com.app.modules.rh.dto.EcheanceResponse;
import com.app.modules.rh.dto.RenouvellementCddRequest;
import com.app.modules.rh.dto.TraiterEcheanceRequest;
import com.app.modules.rh.entity.ConfigAlerteRh;
import com.app.modules.rh.repository.ConfigAlerteRhRepository;
import com.app.modules.rh.service.ContratService;
import com.app.modules.rh.service.EcheanceService;
import com.app.modules.rh.service.FormationObligatoireService;
import com.app.modules.rh.service.TitreSejourService;
import com.app.modules.rh.service.VisiteMedicaleService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContratControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final ContratService contratService = mock(ContratService.class);
    private final EcheanceService echeanceService = mock(EcheanceService.class);
    private final VisiteMedicaleService visiteMedicaleService = mock(VisiteMedicaleService.class);
    private final TitreSejourService titreSejourService = mock(TitreSejourService.class);
    private final FormationObligatoireService formationService = mock(FormationObligatoireService.class);
    private final ConfigAlerteRhRepository configRepository = mock(ConfigAlerteRhRepository.class);

    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SALARIE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID CONTRAT_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID ECHEANCE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ContratController(
                                        contratService,
                                        echeanceService,
                                        visiteMedicaleService,
                                        titreSejourService,
                                        formationService,
                                        configRepository))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(
                                new AuthenticationPrincipalArgumentResolver(),
                                new PageableHandlerMethodArgumentResolver())
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
        u.setEmail("rh@test.com");
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

    private static ContratResponse sampleContrat() {
        return new ContratResponse(
                CONTRAT_ID,
                SALARIE_ID,
                "Doe Jane",
                "EMP-1",
                "RH",
                "CDD",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                null,
                1,
                "CTR-001",
                "Analyste",
                "Surcroît",
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                true,
                90,
                "NORMAL",
                LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    private static EcheanceResponse sampleEcheance() {
        return new EcheanceResponse(
                ECHEANCE_ID,
                SALARIE_ID,
                "Doe Jane",
                "RH",
                "EMP-1",
                "FIN_CDD",
                "Fin CDD",
                null,
                LocalDate.of(2026, 12, 31),
                "ACTIVE",
                2,
                null,
                null,
                null,
                null,
                null,
                70,
                "ATTENTION",
                LocalDateTime.now());
    }

    @Test
    void list_ok() throws Exception {
        when(contratService.listContrats(eq(ORG_ID), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleContrat()), PageRequest.of(0, 20), 1));

        withAuth(auth(Role.RH), () -> mockMvc.perform(get("/api/v1/rh/contrats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].typeContrat").value("CDD")));
    }

    @Test
    void creer_ok_201() throws Exception {
        when(contratService.creerContrat(any(ContratRequest.class), eq(SALARIE_ID), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(sampleContrat());

        ContratRequest req = new ContratRequest(
                "CDD",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                null,
                1,
                "CTR-001",
                "Analyste",
                "Surcroît",
                null);

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/rh/contrats/salaries/{id}", SALARIE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(CONTRAT_ID.toString())));
    }

    @Test
    void actif_ok() throws Exception {
        when(contratService.getContratActif(SALARIE_ID, ORG_ID)).thenReturn(sampleContrat());

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(get("/api/v1/rh/contrats/salaries/{id}/actif", SALARIE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actif").value(true)));
    }

    @Test
    void historique_ok() throws Exception {
        when(contratService.historiqueContrats(SALARIE_ID, ORG_ID)).thenReturn(List.of(sampleContrat()));

        withAuth(auth(Role.RH), () -> mockMvc.perform(get("/api/v1/rh/contrats/salaries/{id}/historique", SALARIE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].numeroContrat").value("CTR-001")));
    }

    @Test
    void renouveler_ok() throws Exception {
        when(contratService.renouvelerCdd(eq(CONTRAT_ID), any(RenouvellementCddRequest.class), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(sampleContrat());

        RenouvellementCddRequest req =
                new RenouvellementCddRequest(LocalDate.of(2027, 6, 30), "Besoin", null);

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/rh/contrats/{id}/renouveler", CONTRAT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()));
    }

    @Test
    void decisionFin_ok() throws Exception {
        when(contratService.enregistrerDecisionFin(eq(CONTRAT_ID), any(DecisionFinCddRequest.class), eq(ORG_ID)))
                .thenReturn(sampleContrat());

        DecisionFinCddRequest req = new DecisionFinCddRequest("CDI", LocalDate.of(2026, 11, 1), "OK");

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/rh/contrats/{id}/decision-fin", CONTRAT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()));
    }

    @Test
    void uploadContratSigne_ok() throws Exception {
        when(contratService.uploadContratSigne(eq(CONTRAT_ID), any(), eq(ORG_ID)))
                .thenReturn("missions/contrat.pdf");

        MockMultipartFile file =
                new MockMultipartFile("file", "contrat.pdf", "application/pdf", "%PDF-1.4\n".getBytes());

        withAuth(auth(Role.RH), () -> mockMvc.perform(multipart("/api/v1/rh/contrats/{id}/contrat-signe", CONTRAT_ID).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("missions/contrat.pdf")));
    }

    @Test
    void cddExpirant_ok() throws Exception {
        when(contratService.getCddExpirantDans(eq(ORG_ID), anyInt())).thenReturn(List.of(sampleContrat()));

        withAuth(auth(Role.RH), () -> mockMvc.perform(get("/api/v1/rh/contrats/cdd-expirant").param("jours", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(CONTRAT_ID.toString())));

        verify(contratService).getCddExpirantDans(ORG_ID, 60);
    }

    @Test
    void listEcheances_ok() throws Exception {
        when(echeanceService.listEcheances(eq(ORG_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleEcheance()), PageRequest.of(0, 20), 1));

        withAuth(auth(Role.RH), () -> mockMvc.perform(get("/api/v1/rh/contrats/echeances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].typeEcheance").value("FIN_CDD")));
    }

    @Test
    void creerEcheance_ok_201() throws Exception {
        when(echeanceService.creerEcheance(any(EcheanceRequest.class), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(sampleEcheance());

        EcheanceRequest req = new EcheanceRequest(
                SALARIE_ID, CONTRAT_ID, "FIN_CDD", "Fin CDD", null, LocalDate.of(2026, 12, 31), 2, null);

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/rh/contrats/echeances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()));
    }

    @Test
    void dashboard_ok() throws Exception {
        when(echeanceService.getDashboard(ORG_ID))
                .thenReturn(new EcheanceDashboardResponse(5, 1, 1, 2, 1, 0, 0, 0, 0, List.of(sampleEcheance())));

        withAuth(auth(Role.FINANCIER), () -> mockMvc.perform(get("/api/v1/rh/contrats/echeances/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalActives").value(5)));
    }

    @Test
    void traiterEcheance_ok() throws Exception {
        when(echeanceService.traiterEcheance(eq(ECHEANCE_ID), any(TraiterEcheanceRequest.class), any(), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(sampleEcheance());

        TraiterEcheanceRequest data = new TraiterEcheanceRequest(LocalDate.of(2026, 3, 1), "Traité");
        MockMultipartFile meta =
                new MockMultipartFile(
                        "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(data));

        withAuth(auth(Role.RH), () -> mockMvc.perform(
                        multipart("/api/v1/rh/contrats/echeances/{id}/traiter", ECHEANCE_ID).file(meta))
                .andExpect(status().isOk()));
    }

    @Test
    void annulerEcheance_ok() throws Exception {
        when(echeanceService.annulerEcheance(ECHEANCE_ID, "Doublon", ORG_ID)).thenReturn(sampleEcheance());

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/rh/contrats/echeances/{id}/annuler", ECHEANCE_ID)
                        .param("motif", "Doublon"))
                .andExpect(status().isOk()));
    }

    @Test
    void getConfig_creeSiAbsent() throws Exception {
        when(configRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
        when(configRepository.save(any(ConfigAlerteRh.class))).thenAnswer(inv -> {
            ConfigAlerteRh c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(get("/api/v1/rh/contrats/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
    }

    @Test
    void updateConfig_ok() throws Exception {
        ConfigAlerteRh cfg = new ConfigAlerteRh();
        cfg.setId(UUID.randomUUID());
        cfg.setOrganisationId(ORG_ID);
        when(configRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(cfg));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConfigAlerteRequest req = new ConfigAlerteRequest(45, 10, 20, 60, 30, true, false, false, 2);

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(put("/api/v1/rh/contrats/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alerteFinCddJ").value(45)));
    }
}
