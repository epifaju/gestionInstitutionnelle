package com.app.modules.inventaire.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.inventaire.dto.StatsInventaireResponse;
import com.app.modules.inventaire.service.InventaireService;
import com.app.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventaireControllerMvcTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    private final InventaireService inventaireService = mock(InventaireService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new InventaireController(inventaireService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(
                                new AuthenticationPrincipalArgumentResolver(),
                                new PageableHandlerMethodArgumentResolver())
                        .build();
    }

    private UsernamePasswordAuthenticationToken logistiqueAuth() {
        UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        Organisation o = new Organisation();
        o.setId(orgId);
        o.setNom("Org");
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setOrganisation(o);
        u.setEmail("log@test.com");
        u.setRole(Role.LOGISTIQUE);
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
    void stats_ok_retourneStats() throws Exception {
        StatsInventaireResponse resp =
                new StatsInventaireResponse(
                        new BigDecimal("1234.50"),
                        List.of(new StatsInventaireResponse.CompteParEtat("OK", 10)),
                        List.of(new StatsInventaireResponse.CompteParCategorie("IT", 3)));
        when(inventaireService.getStats(any())).thenReturn(resp);

        withAuth(logistiqueAuth(), () -> mockMvc.perform(get("/api/v1/inventaire/stats").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valeurTotaleParc").value(1234.50))
                .andExpect(jsonPath("$.data.repartitionEtat[0].etat").value("OK")));
    }
}

