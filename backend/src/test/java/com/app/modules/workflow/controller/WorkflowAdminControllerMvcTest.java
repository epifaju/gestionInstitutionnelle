package com.app.modules.workflow.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.workflow.dto.UpsertWorkflowDefinitionRequest;
import com.app.modules.workflow.dto.WorkflowDefinitionDto;
import com.app.modules.workflow.entity.WorkflowDelegation;
import com.app.modules.workflow.service.WorkflowAdminService;
import com.app.modules.workflow.service.WorkflowDelegationService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowAdminControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final WorkflowAdminService adminService = mock(WorkflowAdminService.class);
    private final WorkflowDelegationService delegationService = mock(WorkflowDelegationService.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID DELEG_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID TO_USER = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowAdminController(adminService, delegationService))
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
        u.setEmail("admin@test.com");
        u.setRole(Role.ADMIN);
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
    void list_ok() throws Exception {
        when(adminService.list(ORG_ID)).thenReturn(List.of(
                new WorkflowDefinitionDto(UUID.randomUUID(), "CONGE", true, List.of())));
        withAuth(() -> mockMvc.perform(get("/api/v1/admin/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].processKey").value("CONGE")));
    }

    @Test
    void get_ok() throws Exception {
        when(adminService.get(ORG_ID, "CONGE")).thenReturn(
                new WorkflowDefinitionDto(UUID.randomUUID(), "CONGE", true, List.of()));
        withAuth(() -> mockMvc.perform(get("/api/v1/admin/workflows/{processKey}", "CONGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true)));
    }

    @Test
    void upsert_ok() throws Exception {
        when(adminService.upsert(eq(ORG_ID), any())).thenReturn(
                new WorkflowDefinitionDto(UUID.randomUUID(), "CONGE", false, List.of()));
        withAuth(() -> mockMvc.perform(put("/api/v1/admin/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpsertWorkflowDefinitionRequest("CONGE", false, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processKey").value("CONGE")));
    }

    @Test
    void createDelegation_ok() throws Exception {
        WorkflowDelegation d = new WorkflowDelegation();
        d.setId(DELEG_ID);
        when(delegationService.create(eq(ORG_ID), eq("RH"), eq(TO_USER), any(), any(), eq("vacances"), eq(USER_ID)))
                .thenReturn(d);

        Map<String, Object> body = Map.of(
                "fromRole", "RH",
                "toUserId", TO_USER.toString(),
                "startAt", OffsetDateTime.parse("2026-04-01T00:00:00Z").toString(),
                "endAt", OffsetDateTime.parse("2026-04-15T00:00:00Z").toString(),
                "reason", "vacances");

        withAuth(() -> mockMvc.perform(post("/api/v1/admin/workflows/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(DELEG_ID.toString())));
    }

    @Test
    void deleteDelegation_ok() throws Exception {
        withAuth(() -> mockMvc.perform(delete("/api/v1/admin/workflows/delegations/{id}", DELEG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true)));
        verify(delegationService).delete(ORG_ID, DELEG_ID);
    }
}
