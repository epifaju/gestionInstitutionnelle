package com.app.modules.templates.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.templates.dto.CreateTemplateRequest;
import com.app.modules.templates.dto.TemplateDefinitionResponse;
import com.app.modules.templates.dto.TemplateRevisionResponse;
import com.app.modules.templates.dto.UpdateTemplateRequest;
import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.entity.TemplateStatus;
import com.app.modules.templates.service.TemplateService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TemplateAdminControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TemplateService templateService = mock(TemplateService.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID TPL_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TemplateAdminController(templateService))
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private UsernamePasswordAuthenticationToken auth() {
        Organisation o = new Organisation();
        o.setId(ORG_ID);
        o.setNom("Org");
        Utilisateur u = new Utilisateur();
        u.setId(USER_ID);
        u.setOrganisationId(ORG_ID);
        u.setOrganisation(o);
        u.setEmail("admin@test.com");
        u.setRole(Role.ADMIN);
        CustomUserDetails cud = new CustomUserDetails(u);
        return new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
    }

    private void withAuth(ThrowingRunnable r) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(auth());
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

    private TemplateDefinitionResponse defResp() {
        return new TemplateDefinitionResponse(
                TPL_ID, "MISSION_ORDRE", "Ordre", TemplateCategory.MISSION, TemplateFormat.DOCX,
                TemplateStatus.DRAFT, "fr", Instant.now(), Instant.now());
    }

    @Test
    void list_ok() throws Exception {
        when(templateService.list(ORG_ID)).thenReturn(List.of(defResp()));
        withAuth(() -> mockMvc.perform(get("/api/v1/templates/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("MISSION_ORDRE")));
    }

    @Test
    void create_ok() throws Exception {
        when(templateService.create(any(), eq(ORG_ID), eq(USER_ID))).thenReturn(defResp());
        withAuth(() -> mockMvc.perform(post("/api/v1/templates/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTemplateRequest("MISSION_ORDRE", "Ordre", TemplateCategory.MISSION, TemplateFormat.DOCX, "fr"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TPL_ID.toString())));
    }

    @Test
    void update_ok() throws Exception {
        when(templateService.update(eq(TPL_ID), any(), eq(ORG_ID), eq(USER_ID))).thenReturn(defResp());
        withAuth(() -> mockMvc.perform(put("/api/v1/templates/admin/{id}", TPL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTemplateRequest("Ordre", TemplateStatus.ACTIVE, "fr"))))
                .andExpect(status().isOk()));
        verify(templateService).update(eq(TPL_ID), any(UpdateTemplateRequest.class), eq(ORG_ID), eq(USER_ID));
    }

    @Test
    void revisions_ok() throws Exception {
        when(templateService.listRevisions(TPL_ID, ORG_ID)).thenReturn(List.of(
                new TemplateRevisionResponse(UUID.randomUUID(), 1, UUID.randomUUID(), null, "mime", "chk", "c", USER_ID, Instant.now())));
        withAuth(() -> mockMvc.perform(get("/api/v1/templates/admin/{id}/revisions", TPL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value(1)));
    }

    @Test
    void addRevision_ok() throws Exception {
        when(templateService.addRevision(eq(TPL_ID), any(), isNull(), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(new TemplateRevisionResponse(UUID.randomUUID(), 2, UUID.randomUUID(), null, "mime", "chk", null, USER_ID, Instant.now()));
        MockMultipartFile file = new MockMultipartFile("file", "t.docx", "application/octet-stream", "x".getBytes());
        withAuth(() -> mockMvc.perform(multipart("/api/v1/templates/admin/{id}/revisions", TPL_ID).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2)));
    }
}
