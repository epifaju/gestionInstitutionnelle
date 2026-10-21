package com.app.modules.templates.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.templates.dto.GenerateTemplateRequest;
import com.app.modules.templates.dto.GeneratedDocumentResponse;
import com.app.modules.templates.service.TemplateOutputFormat;
import com.app.modules.templates.service.TemplateService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TemplateGenerateControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TemplateService templateService = mock(TemplateService.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID GEN_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TemplateGenerateController(templateService))
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private void withAuth(ThrowingRunnable r) throws Exception {
        Organisation o = new Organisation();
        o.setId(ORG_ID);
        Utilisateur u = new Utilisateur();
        u.setId(USER_ID);
        u.setOrganisationId(ORG_ID);
        u.setOrganisation(o);
        u.setEmail("u@test.com");
        u.setRole(Role.RH);
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
    void generate_ok_defaultsSaveToGed() throws Exception {
        when(templateService.generate(
                        eq("MISSION_ORDRE"),
                        eq("MISSION"),
                        eq(SUBJECT_ID),
                        eq(TemplateOutputFormat.DOCX),
                        eq(true),
                        any(),
                        eq(ORG_ID),
                        eq(USER_ID)))
                .thenReturn(new GeneratedDocumentResponse(
                        GEN_ID, UUID.randomUUID(), "MISSION", SUBJECT_ID, null, "DOCX", USER_ID, Instant.now()));

        withAuth(() -> mockMvc.perform(post("/api/v1/templates/{code}/generate", "MISSION_ORDRE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GenerateTemplateRequest(
                                "MISSION", SUBJECT_ID, TemplateOutputFormat.DOCX, null, null, Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(GEN_ID.toString())));
    }

    @Test
    void listGenerated_ok() throws Exception {
        when(templateService.listGenerated(ORG_ID, "MISSION", SUBJECT_ID)).thenReturn(List.of(
                new GeneratedDocumentResponse(GEN_ID, UUID.randomUUID(), "MISSION", SUBJECT_ID, null, "PDF", USER_ID, Instant.now())));

        withAuth(() -> mockMvc.perform(get("/api/v1/templates/generated")
                        .param("subjectType", "MISSION")
                        .param("subjectId", SUBJECT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outputFormat").value("PDF")));
        verify(templateService).listGenerated(ORG_ID, "MISSION", SUBJECT_ID);
    }
}
