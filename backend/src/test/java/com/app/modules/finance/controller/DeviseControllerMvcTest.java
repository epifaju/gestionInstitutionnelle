package com.app.modules.finance.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.finance.entity.TauxChangeHistorique;
import com.app.modules.finance.repository.TauxChangeHistoriqueRepository;
import com.app.modules.finance.service.ExchangeRateService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviseControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
    private final TauxChangeHistoriqueRepository historiqueRepository = mock(TauxChangeHistoriqueRepository.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviseController(exchangeRateService, historiqueRepository))
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private void withAuth(Role role, ThrowingRunnable r) throws Exception {
        Organisation o = new Organisation();
        o.setId(ORG_ID);
        Utilisateur u = new Utilisateur();
        u.setId(USER_ID);
        u.setOrganisationId(ORG_ID);
        u.setOrganisation(o);
        u.setEmail("u@test.com");
        u.setRole(role);
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

    @Test
    void tauxDuJour_ok() throws Exception {
        when(exchangeRateService.getTousLesTauxDuJour("EUR")).thenReturn(Map.of("USD", BigDecimal.valueOf(1.08)));
        withAuth(Role.FINANCIER, () -> mockMvc.perform(get("/api/v1/devises/taux-du-jour"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.USD").value(1.08)));
    }

    @Test
    void tauxALaDate_ok() throws Exception {
        LocalDate date = LocalDate.of(2026, 3, 1);
        when(exchangeRateService.getTauxALaDate(ORG_ID, "EUR", "USD", date)).thenReturn(BigDecimal.valueOf(1.1));
        withAuth(Role.FINANCIER, () -> mockMvc.perform(get("/api/v1/devises/taux/{devise1}/{devise2}/{date}", "EUR", "USD", date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1.1)));
    }

    @Test
    void convertir_ok_avecDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 3, 1);
        when(exchangeRateService.convertir(ORG_ID, BigDecimal.TEN, "EUR", "USD", date)).thenReturn(BigDecimal.valueOf(11));
        when(exchangeRateService.getTauxALaDate(ORG_ID, "EUR", "USD", date)).thenReturn(BigDecimal.valueOf(1.1));

        withAuth(Role.FINANCIER, () -> mockMvc.perform(get("/api/v1/devises/convertir")
                        .param("montant", "10")
                        .param("de", "EUR")
                        .param("vers", "USD")
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultat").value(11))
                .andExpect(jsonPath("$.data.taux").value(1.1)));
    }

    @Test
    void historique_ok() throws Exception {
        when(historiqueRepository.findByDeviseBaseAndDeviseCibleAndDateTauxBetweenOrderByDateTauxAsc(
                        eq("EUR"), eq("USD"), any(), any()))
                .thenReturn(List.of(new TauxChangeHistorique()));

        withAuth(Role.FINANCIER, () -> mockMvc.perform(get("/api/v1/devises/historique/{devise1}/{devise2}", "eur", "usd")
                        .param("debut", "2026-01-01")
                        .param("fin", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray()));
        verify(historiqueRepository).findByDeviseBaseAndDeviseCibleAndDateTauxBetweenOrderByDateTauxAsc(
                eq("EUR"), eq("USD"), any(), any());
    }
}
