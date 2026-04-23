package com.app.modules.budget.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.budget.dto.BudgetResponse;
import com.app.modules.budget.dto.LigneBudgetResponse;
import com.app.modules.budget.service.BudgetService;
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

class BudgetControllerMvcTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    private final BudgetService budgetService = mock(BudgetService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new BudgetController(budgetService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    private UsernamePasswordAuthenticationToken financierAuth() {
        UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        Organisation o = new Organisation();
        o.setId(orgId);
        o.setNom("Org");
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setOrganisation(o);
        u.setEmail("fin@test.com");
        u.setRole(Role.FINANCIER);
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
    void getBudget_ok_retourneBudget() throws Exception {
        UUID budgetId = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        UUID ligneId = UUID.fromString("d0000000-0000-0000-0000-000000000001");
        UUID catId = UUID.fromString("e0000000-0000-0000-0000-000000000001");

        LigneBudgetResponse ligne = new LigneBudgetResponse(
                ligneId,
                catId,
                "Voyages",
                "DEPENSE",
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                new BigDecimal("20.0"),
                false);
        BudgetResponse resp = new BudgetResponse(
                budgetId,
                2026,
                "BROUILLON",
                (LocalDateTime) null,
                List.of(ligne),
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"));

        when(budgetService.getBudget(any(), eq(2026))).thenReturn(resp);

        withAuth(financierAuth(), () -> mockMvc.perform(get("/api/v1/budget/{annee}", 2026).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.annee").value(2026))
                .andExpect(jsonPath("$.data.lignes[0].categorieLibelle").value("Voyages")));

        verify(budgetService).getBudget(any(), eq(2026));
    }
}

