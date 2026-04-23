package com.app.modules.rapports.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rapports.dto.DashboardResponse;
import com.app.modules.rapports.service.RapportService;
import com.app.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RapportControllerMvcTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    private final RapportService rapportService = mock(RapportService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new RapportController(rapportService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        Organisation o = new Organisation();
        o.setId(orgId);
        o.setNom("Org");
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setOrganisation(o);
        u.setEmail("admin@test.com");
        u.setRole(Role.ADMIN);
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

    @Test
    void dashboard_ok_retourneDashboard() throws Exception {
        DashboardResponse resp =
                new DashboardResponse(
                        new DashboardResponse.MoisCourant(2026, 4),
                        new DashboardResponse.DashboardKpis(
                                new BigDecimal("100.00"),
                                new BigDecimal("50.00"),
                                new BigDecimal("-50.00"),
                                10,
                                2,
                                new BigDecimal("999.00")),
                        List.of(new DashboardResponse.EvolutionMois("2026-04", new BigDecimal("100.00"), new BigDecimal("50.00"))),
                        List.of(),
                        List.of(),
                        List.of(new DashboardResponse.CongeEnCoursItem("Doe John", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10))));

        when(rapportService.getDashboard(any(), any(), eq(Role.ADMIN))).thenReturn(resp);

        withAuth(adminAuth(), () -> mockMvc.perform(get("/api/v1/rapports/dashboard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.moisCourant.annee").value(2026))
                .andExpect(jsonPath("$.data.kpis.effectifsActifs").value(10)));
    }
}

