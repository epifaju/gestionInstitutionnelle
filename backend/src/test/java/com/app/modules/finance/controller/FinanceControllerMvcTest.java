package com.app.modules.finance.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.finance.dto.StatsResponse;
import com.app.modules.finance.service.CategorieService;
import com.app.modules.finance.service.FactureService;
import com.app.modules.finance.service.PaiementService;
import com.app.modules.finance.service.RecetteService;
import com.app.modules.finance.service.StatsService;
import com.app.modules.finance.service.TauxChangeService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceControllerMvcTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    private final FactureService factureService = mock(FactureService.class);
    private final PaiementService paiementService = mock(PaiementService.class);
    private final RecetteService recetteService = mock(RecetteService.class);
    private final StatsService statsService = mock(StatsService.class);
    private final CategorieService categorieService = mock(CategorieService.class);
    private final TauxChangeService tauxChangeService = mock(TauxChangeService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new FinanceController(
                                factureService,
                                paiementService,
                                recetteService,
                                statsService,
                                categorieService,
                                tauxChangeService))
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
    void stats_ok_retourneApiResponseOk() throws Exception {
        StatsResponse resp = new StatsResponse(
                2026,
                4,
                new BigDecimal("1200.00"),
                new BigDecimal("500.00"),
                new BigDecimal("-700.00"),
                "EUR",
                10,
                2,
                List.of(),
                List.of());
        when(statsService.getStatsMensuelles(any(), eq(2026), eq(4))).thenReturn(resp);

        withAuth(financierAuth(), () -> mockMvc.perform(get("/api/v1/finance/stats/{annee}/{mois}", 2026, 4)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.annee").value(2026))
                .andExpect(jsonPath("$.data.mois").value(4))
                .andExpect(jsonPath("$.data.devise").value("EUR")));
    }
}

