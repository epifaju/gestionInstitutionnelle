package com.app.modules.rh.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.SalarieResponse;
import com.app.modules.rh.service.CongeService;
import com.app.modules.rh.service.PaieService;
import com.app.modules.rh.service.SalarieService;
import com.app.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RhControllerMvcTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    private final SalarieService salarieService = mock(SalarieService.class);
    private final CongeService congeService = mock(CongeService.class);
    private final PaieService paieService = mock(PaieService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new RhController(salarieService, congeService, paieService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(
                                new AuthenticationPrincipalArgumentResolver(),
                                new PageableHandlerMethodArgumentResolver())
                        .build();
    }

    private UsernamePasswordAuthenticationToken rhAuth() {
        UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        Organisation o = new Organisation();
        o.setId(orgId);
        o.setNom("Org");
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setOrganisation(o);
        u.setEmail("rh@test.com");
        u.setRole(Role.RH);
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
    void listSalaries_ok_retournePageResponse() throws Exception {
        UUID salarieId = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        SalarieResponse row =
                new SalarieResponse(
                        salarieId,
                        "EMP-0001",
                        "Doe",
                        "John",
                        "x@test.com",
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

        var page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(salarieService.listSalaries(any(), any(), any(), any(), any())).thenReturn(page);

        withAuth(rhAuth(), () -> mockMvc.perform(get("/api/v1/rh/salaries").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].matricule").value("EMP-0001")));

        verify(salarieService).listSalaries(any(), any(), any(), any(), any());
    }

    @Test
    void meSalarie_ok_retourneSalarie() throws Exception {
        UUID salarieId = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        SalarieResponse row =
                new SalarieResponse(
                        salarieId,
                        "EMP-0001",
                        "Doe",
                        "John",
                        "x@test.com",
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

        when(salarieService.getMe(any(), any(), eq("rh@test.com"))).thenReturn(row);

        withAuth(rhAuth(), () -> mockMvc.perform(get("/api/v1/rh/me/salarie").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(salarieId.toString())));
    }
}

