package com.app.modules.courriers.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.courriers.dto.CourrierDocumentRequest;
import com.app.modules.courriers.dto.CourrierRequest;
import com.app.modules.courriers.dto.CourrierResponse;
import com.app.modules.courriers.dto.CourrierStatutRequest;
import com.app.modules.courriers.service.CourrierService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourrierControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CourrierService courrierService = mock(CourrierService.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID COURRIER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID DOC_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CourrierController(courrierService))
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
        u.setEmail("user@test.com");
        u.setRole(role);
        CustomUserDetails cud = new CustomUserDetails(u);
        return new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
    }

    private void withAuth(Role role, ThrowingRunnable r) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(auth(role));
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

    private CourrierResponse sample() {
        return new CourrierResponse(
                COURRIER_ID,
                "ENTREE",
                "BROUILLON",
                "REF-1",
                "Objet",
                "Exp",
                "Dest",
                LocalDate.of(2026, 1, 15),
                null,
                null,
                "EMAIL",
                null,
                null,
                USER_ID,
                Instant.parse("2026-01-15T10:00:00Z"),
                Instant.parse("2026-01-15T10:00:00Z"));
    }

    @Test
    void list_ok() throws Exception {
        when(courrierService.list(eq(ORG_ID), eq("ENTREE"), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        withAuth(Role.FINANCIER, () -> mockMvc.perform(get("/api/v1/courriers").param("sens", "ENTREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].objet").value("Objet")));
        verify(courrierService).list(eq(ORG_ID), eq("ENTREE"), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void create_ok() throws Exception {
        CourrierRequest body = new CourrierRequest(
                "SORTIE", null, null, "Note verbale", "Ambassade", "MAE", LocalDate.of(2026, 2, 1),
                null, LocalDate.of(2026, 2, 1), "COURRIER_POSTAL", null, null);
        when(courrierService.create(any(CourrierRequest.class), eq(ORG_ID), eq(USER_ID))).thenReturn(sample());

        withAuth(Role.RH, () -> mockMvc.perform(post("/api/v1/courriers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(COURRIER_ID.toString())));
        verify(courrierService).create(any(CourrierRequest.class), eq(ORG_ID), eq(USER_ID));
    }

    @Test
    void update_ok() throws Exception {
        CourrierRequest body = new CourrierRequest(
                "ENTREE", "ENREGISTRE", "REF-1", "Objet", "Exp", "Dest", LocalDate.of(2026, 1, 15),
                null, null, "EMAIL", null, null);
        when(courrierService.update(eq(COURRIER_ID), any(CourrierRequest.class), eq(ORG_ID), eq(false)))
                .thenReturn(sample());

        withAuth(Role.RH, () -> mockMvc.perform(put("/api/v1/courriers/{id}", COURRIER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()));
        verify(courrierService).update(eq(COURRIER_ID), any(CourrierRequest.class), eq(ORG_ID), eq(false));
    }

    @Test
    void changerStatut_ok() throws Exception {
        when(courrierService.changerStatut(eq(COURRIER_ID), eq("RECU"), eq(ORG_ID), eq(true)))
                .thenReturn(sample());

        withAuth(Role.ADMIN, () -> mockMvc.perform(patch("/api/v1/courriers/{id}/statut", COURRIER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CourrierStatutRequest("RECU"))))
                .andExpect(status().isOk()));
        verify(courrierService).changerStatut(eq(COURRIER_ID), eq("RECU"), eq(ORG_ID), eq(true));
    }

    @Test
    void rattacherDocument_ok() throws Exception {
        when(courrierService.rattacherDocument(eq(COURRIER_ID), eq(DOC_ID), eq(ORG_ID), anyBoolean()))
                .thenReturn(sample());

        withAuth(Role.RH, () -> mockMvc.perform(post("/api/v1/courriers/{id}/document", COURRIER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CourrierDocumentRequest(DOC_ID))))
                .andExpect(status().isOk()));
        verify(courrierService).rattacherDocument(eq(COURRIER_ID), eq(DOC_ID), eq(ORG_ID), eq(false));
    }

    @Test
    void get_ok() throws Exception {
        when(courrierService.getById(COURRIER_ID, ORG_ID)).thenReturn(sample());

        withAuth(Role.FINANCIER, () -> mockMvc.perform(get("/api/v1/courriers/{id}", COURRIER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sens").value("ENTREE")));
    }
}
