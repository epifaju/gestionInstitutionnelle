package com.app.modules.rapports.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rapports.dto.todo.QuickActionRequest;
import com.app.modules.rapports.dto.todo.QuickActionResponse;
import com.app.modules.rapports.dto.todo.TodoDashboardResponse;
import com.app.modules.rapports.service.TodoDashboardService;
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

import java.time.LocalDateTime;
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

class TodoDashboardControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TodoDashboardService todoDashboardService = mock(TodoDashboardService.class);

    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new TodoDashboardController(todoDashboardService))
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

    @Test
    void getDashboard_ok() throws Exception {
        when(todoDashboardService.getTodoDashboard(ORG_ID, "ADMIN", USER_ID))
                .thenReturn(new TodoDashboardResponse(LocalDateTime.now(), "ADMIN", 0, 0, List.of(), Map.of()));

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(get("/api/v1/todo-dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleUtilisateur").value("ADMIN")));

        verify(todoDashboardService).getTodoDashboard(ORG_ID, "ADMIN", USER_ID);
    }

    @Test
    void getDashboard_roleRh() throws Exception {
        when(todoDashboardService.getTodoDashboard(ORG_ID, "RH", USER_ID))
                .thenReturn(new TodoDashboardResponse(LocalDateTime.now(), "RH", 1, 0, List.of(), Map.of()));

        withAuth(auth(Role.RH), () -> mockMvc.perform(get("/api/v1/todo-dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalActionsGlobal").value(1)));

        verify(todoDashboardService).getTodoDashboard(ORG_ID, "RH", USER_ID);
    }

    @Test
    void comptages_ok() throws Exception {
        when(todoDashboardService.getComptages(ORG_ID, "FINANCIER", USER_ID))
                .thenReturn(Map.of("total", 5L, "factures_a_payer", 5L));

        withAuth(auth(Role.FINANCIER), () -> mockMvc.perform(get("/api/v1/todo-dashboard/comptages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5)));
    }

    @Test
    void action_ok() throws Exception {
        when(todoDashboardService.executerActionRapide(eq(ITEM_ID), eq("CONGE"), any(QuickActionRequest.class), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(new QuickActionResponse(true, "Congé validé", "VALIDE", 2));

        withAuth(auth(Role.RH), () -> mockMvc.perform(post("/api/v1/todo-dashboard/action/{type}/{id}", "CONGE", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuickActionRequest("VALIDER_CONGE", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succes").value(true))
                .andExpect(jsonPath("$.data.nouveauStatut").value("VALIDE")));
    }
}
