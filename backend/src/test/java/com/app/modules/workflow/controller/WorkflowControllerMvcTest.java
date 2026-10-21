package com.app.modules.workflow.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.workflow.dto.WorkflowDecisionRequest;
import com.app.modules.workflow.entity.WorkflowAction;
import com.app.modules.workflow.entity.WorkflowDecision;
import com.app.modules.workflow.entity.WorkflowInstance;
import com.app.modules.workflow.entity.WorkflowInstanceStatus;
import com.app.modules.workflow.service.WorkflowEngineService;
import com.app.modules.workflow.service.WorkflowRuleMatch;
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

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final WorkflowEngineService workflowEngineService = mock(WorkflowEngineService.class);

    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new WorkflowController(workflowEngineService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
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

    private WorkflowInstance sampleInstance(WorkflowInstanceStatus status) {
        WorkflowInstance inst = new WorkflowInstance();
        inst.setId(INSTANCE_ID);
        inst.setOrganisationId(ORG_ID);
        inst.setProcessKey("MISSION_APPROVAL");
        inst.setSubjectType("Mission");
        inst.setSubjectId(SUBJECT_ID);
        inst.setAmountEur(new BigDecimal("100.00"));
        inst.setStatus(status);
        inst.setCurrentLevel(1);
        inst.setCreatedBy(USER_ID);
        inst.setCreatedAt(Instant.parse("2026-01-15T10:00:00Z"));
        return inst;
    }

    private WorkflowRuleMatch sampleRule() {
        return new WorkflowRuleMatch(1, "RH", null, null, null);
    }

    @Test
    void listInstances_ok() throws Exception {
        when(workflowEngineService.listBySubject(ORG_ID, "Mission", SUBJECT_ID))
                .thenReturn(List.of(sampleInstance(WorkflowInstanceStatus.PENDING)));
        when(workflowEngineService.listActions(INSTANCE_ID)).thenReturn(List.of());

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/workflows/instances")
                        .param("subjectType", "Mission")
                        .param("subjectId", SUBJECT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(INSTANCE_ID.toString()))
                .andExpect(jsonPath("$.data[0].processKey").value("MISSION_APPROVAL"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING")));
    }

    @Test
    void submit_ok() throws Exception {
        when(workflowEngineService.submitIfAbsent(
                        eq(ORG_ID), eq("MISSION_APPROVAL"), eq("Mission"), eq(SUBJECT_ID), any(), eq(USER_ID)))
                .thenReturn(sampleInstance(WorkflowInstanceStatus.PENDING));
        when(workflowEngineService.listActions(INSTANCE_ID)).thenReturn(List.of());

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(post(
                                "/api/v1/workflows/{processKey}/{subjectType}/{subjectId}/submit",
                                "MISSION_APPROVAL",
                                "Mission",
                                SUBJECT_ID)
                        .param("amountEur", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectId").value(SUBJECT_ID.toString())));

        verify(workflowEngineService)
                .submitIfAbsent(ORG_ID, "MISSION_APPROVAL", "Mission", SUBJECT_ID, new BigDecimal("100.00"), USER_ID);
    }

    @Test
    void approve_ok() throws Exception {
        WorkflowInstance pending = sampleInstance(WorkflowInstanceStatus.PENDING);
        WorkflowInstance approved = sampleInstance(WorkflowInstanceStatus.APPROVED);
        approved.setCompletedAt(Instant.parse("2026-01-16T10:00:00Z"));

        when(workflowEngineService.getInstance(ORG_ID, INSTANCE_ID)).thenReturn(pending);
        when(workflowEngineService.resolveRule(ORG_ID, "MISSION_APPROVAL", pending.getAmountEur()))
                .thenReturn(sampleRule());
        when(workflowEngineService.approve(eq(ORG_ID), eq(INSTANCE_ID), any(), eq(USER_ID), eq("RH"), eq("OK")))
                .thenReturn(approved);
        when(workflowEngineService.listActions(INSTANCE_ID)).thenReturn(List.of());

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/workflows/instances/{id}/approve", INSTANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowDecisionRequest("OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED")));
    }

    @Test
    void reject_ok() throws Exception {
        WorkflowInstance pending = sampleInstance(WorkflowInstanceStatus.PENDING);
        WorkflowInstance rejected = sampleInstance(WorkflowInstanceStatus.REJECTED);

        when(workflowEngineService.getInstance(ORG_ID, INSTANCE_ID)).thenReturn(pending);
        when(workflowEngineService.resolveRule(ORG_ID, "MISSION_APPROVAL", pending.getAmountEur()))
                .thenReturn(sampleRule());
        when(workflowEngineService.reject(eq(ORG_ID), eq(INSTANCE_ID), any(), eq(USER_ID), eq("ADMIN"), isNull()))
                .thenReturn(rejected);
        when(workflowEngineService.listActions(INSTANCE_ID)).thenReturn(List.of());

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(post("/api/v1/workflows/instances/{id}/reject", INSTANCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED")));
    }

    @Test
    void approve_avecActions_mappeHistorique() throws Exception {
        WorkflowInstance pending = sampleInstance(WorkflowInstanceStatus.PENDING);
        WorkflowInstance approved = sampleInstance(WorkflowInstanceStatus.APPROVED);

        WorkflowAction action = new WorkflowAction();
        action.setId(UUID.fromString("f0000000-0000-0000-0000-000000000001"));
        action.setLevel(1);
        action.setActorUserId(USER_ID);
        action.setActorRole("RH");
        action.setDecision(WorkflowDecision.APPROVE);
        action.setComment("Validé");
        action.setCreatedAt(Instant.parse("2026-01-16T09:00:00Z"));

        when(workflowEngineService.getInstance(ORG_ID, INSTANCE_ID)).thenReturn(pending);
        when(workflowEngineService.resolveRule(any(), any(), any())).thenReturn(sampleRule());
        when(workflowEngineService.approve(any(), any(), any(), any(), any(), any())).thenReturn(approved);
        when(workflowEngineService.listActions(INSTANCE_ID)).thenReturn(List.of(action));

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/workflows/instances/{id}/approve", INSTANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actions[0].actorRole").value("RH"))
                .andExpect(jsonPath("$.data.actions[0].decision").value("APPROVE")));
    }
}
