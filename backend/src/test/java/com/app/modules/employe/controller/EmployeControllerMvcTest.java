package com.app.modules.employe.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.employe.dto.EmployeCongeRequest;
import com.app.modules.notifications.dto.NotificationResponse;
import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.notifications.service.NotificationService;
import com.app.modules.rh.dto.CongeResponse;
import com.app.modules.rh.dto.DroitsCongesDto;
import com.app.modules.rh.dto.PaieResponse;
import com.app.modules.rh.dto.SalarieResponse;
import com.app.modules.rh.service.CongeService;
import com.app.modules.rh.service.PaieService;
import com.app.modules.rh.service.SalarieService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmployeControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SalarieService salarieService = mock(SalarieService.class);
    private final CongeService congeService = mock(CongeService.class);
    private final PaieService paieService = mock(PaieService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SALARIE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EmployeController(salarieService, congeService, paieService, notificationService))
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new ResourceHttpMessageConverter())
                .setValidator(validator)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private void withAuth(ThrowingRunnable r) throws Exception {
        Organisation o = new Organisation();
        o.setId(ORG_ID);
        Utilisateur u = new Utilisateur();
        u.setId(USER_ID);
        u.setOrganisationId(ORG_ID);
        u.setOrganisation(o);
        u.setEmail("employe@test.com");
        u.setRole(Role.EMPLOYE);
        CustomUserDetails cud = new CustomUserDetails(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities()));
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

    private SalarieResponse salarieResp() {
        return new SalarieResponse(
                SALARIE_ID, "M001", "Dupont", "Alice", "employe@test.com", null, "Dev", "IT",
                LocalDate.of(2024, 1, 1), "CDI", "ACTIF", null, null, null, null, LocalDateTime.now());
    }

    @Test
    void profil_ok() throws Exception {
        when(salarieService.getMeStrict(ORG_ID, USER_ID)).thenReturn(salarieResp());
        withAuth(() -> mockMvc.perform(get("/api/v1/employe/profil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matricule").value("M001")));
    }

    @Test
    void mesConges_ok() throws Exception {
        when(congeService.listMesConges(eq(ORG_ID), eq(USER_ID), any())).thenReturn(new PageImpl<>(
                List.of(new CongeResponse(UUID.randomUUID(), SALARIE_ID, "Dupont Alice", "IT", "CP",
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), BigDecimal.valueOf(5),
                        "EN_ATTENTE", null, null, null, null, LocalDateTime.now())),
                PageRequest.of(0, 20),
                1));
        withAuth(() -> mockMvc.perform(get("/api/v1/employe/conges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].typeConge").value("CP")));
    }

    @Test
    void mesDroits_ok() throws Exception {
        when(congeService.getMesDroitsConges(ORG_ID, USER_ID))
                .thenReturn(new DroitsCongesDto(2026, BigDecimal.valueOf(25), BigDecimal.ZERO, BigDecimal.valueOf(25)));
        withAuth(() -> mockMvc.perform(get("/api/v1/employe/droits-conges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joursRestants").value(25)));
    }

    @Test
    void soumettreConge_ok() throws Exception {
        when(salarieService.getMeStrict(ORG_ID, USER_ID)).thenReturn(salarieResp());
        when(congeService.soumettre(any(), eq(false), eq(ORG_ID), eq(USER_ID))).thenReturn(
                new CongeResponse(UUID.randomUUID(), SALARIE_ID, "Dupont Alice", "IT", "CP",
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), BigDecimal.valueOf(3),
                        "EN_ATTENTE", null, null, null, "vacances", LocalDateTime.now()));

        withAuth(() -> mockMvc.perform(post("/api/v1/employe/conges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeCongeRequest(
                                "CP", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), "vacances"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("EN_ATTENTE")));
    }

    @Test
    void mesFichesPaie_ok() throws Exception {
        when(paieService.listMyPaie(eq(ORG_ID), eq(USER_ID), eq(2026), any())).thenReturn(new PageImpl<>(
                List.of(new PaieResponse(UUID.randomUUID(), SALARIE_ID, "Dupont Alice", "M001", 4, 2026,
                        BigDecimal.valueOf(2800), "EUR", LocalDate.of(2026, 4, 24), "VIREMENT", "PAYE", null, true)),
                PageRequest.of(0, 12),
                1));
        withAuth(() -> mockMvc.perform(get("/api/v1/employe/paie/{annee}", 2026))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mois").value(4)));
    }

    @Test
    void fichePdf_ok() throws Exception {
        when(paieService.downloadMyBulletin(eq(ORG_ID), eq(USER_ID), eq("employe@test.com"), eq(2026), eq(4)))
                .thenReturn(new MinioStorageService.Download(
                        "payslips/x.pdf", "application/pdf", 3, new ByteArrayInputStream(new byte[]{1, 2, 3})));
        withAuth(() -> mockMvc.perform(get("/api/v1/employe/paie/{annee}/{mois}/fiche-pdf", 2026, 4))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("fiche-paie-2026-04.pdf"))));
    }

    @Test
    void mesNotifications_ok() throws Exception {
        when(notificationService.getMesNotifications(eq(USER_ID), anyBoolean(), any())).thenReturn(new PageImpl<>(
                List.of(new NotificationResponse(UUID.randomUUID(), NotificationType.CONGE_VALIDE, "Titre", "Msg", null, false, LocalDateTime.now())),
                PageRequest.of(0, 20),
                1));
        withAuth(() -> mockMvc.perform(get("/api/v1/employe/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].titre").value("Titre")));
        verify(notificationService).getMesNotifications(eq(USER_ID), eq(false), any());
    }
}
