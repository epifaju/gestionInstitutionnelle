package com.app.modules.templates.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateDefinition;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.entity.TemplateRevision;
import com.app.modules.templates.entity.TemplateStatus;
import com.app.modules.templates.repository.TemplateDefinitionRepository;
import com.app.modules.templates.repository.TemplateRevisionRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TemplateCatalogControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TemplateDefinitionRepository definitionRepository = mock(TemplateDefinitionRepository.class);
    private final TemplateRevisionRepository revisionRepository = mock(TemplateRevisionRepository.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID TPL_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TemplateCatalogController(definitionRepository, revisionRepository))
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
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
    void listAvailable_mission_ok() throws Exception {
        TemplateDefinition d = new TemplateDefinition();
        d.setId(TPL_ID);
        d.setCode("MISSION_ORDRE");
        d.setLabel("Ordre");
        d.setCategory(TemplateCategory.MISSION);
        d.setFormat(TemplateFormat.DOCX);
        when(definitionRepository.findByOrganisationIdAndCategoryAndStatus(ORG_ID, TemplateCategory.MISSION, TemplateStatus.ACTIVE))
                .thenReturn(List.of(d));
        TemplateRevision rev = new TemplateRevision();
        rev.setVersion(3);
        when(revisionRepository.findTopByTemplate_IdOrderByVersionDesc(TPL_ID)).thenReturn(Optional.of(rev));

        withAuth(() -> mockMvc.perform(get("/api/v1/templates/catalog").param("subjectType", "MISSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("MISSION_ORDRE"))
                .andExpect(jsonPath("$.data[0].hasRevision").value(true))
                .andExpect(jsonPath("$.data[0].latestVersion").value(3))
                .andExpect(jsonPath("$.data[0].allowedOutputs[0]").value("PDF")));
    }

    @Test
    void listAvailable_htmlOutputs() throws Exception {
        TemplateDefinition d = new TemplateDefinition();
        d.setId(TPL_ID);
        d.setCode("PV_HTML");
        d.setLabel("PV");
        d.setCategory(TemplateCategory.PV);
        d.setFormat(TemplateFormat.HTML);
        when(definitionRepository.findByOrganisationIdAndCategoryAndStatus(ORG_ID, TemplateCategory.PV, TemplateStatus.ACTIVE))
                .thenReturn(List.of(d));
        when(revisionRepository.findTopByTemplate_IdOrderByVersionDesc(TPL_ID)).thenReturn(Optional.empty());

        withAuth(() -> mockMvc.perform(get("/api/v1/templates/catalog").param("subjectType", "PV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hasRevision").value(false))
                .andExpect(jsonPath("$.data[0].allowedOutputs[0]").value("HTML")));
    }

    @Test
    void listAvailable_subjectUnsupported() throws Exception {
        withAuth(() -> mockMvc.perform(get("/api/v1/templates/catalog").param("subjectType", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_SUBJECT_UNSUPPORTED")));
    }
}
