package com.app.modules.notifications.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.notifications.dto.NotificationResponse;
import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.notifications.service.NotificationService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final NotificationService notificationService = mock(NotificationService.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID NOTIF_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
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
        u.setRole(Role.EMPLOYE);
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
    void mesNotifications_ok() throws Exception {
        when(notificationService.getMesNotifications(eq(USER_ID), anyBoolean(), any())).thenReturn(new PageImpl<>(
                List.of(new NotificationResponse(NOTIF_ID, NotificationType.CONGE_SOUMIS, "Congé", "soumis", null, false, LocalDateTime.now())),
                PageRequest.of(0, 20),
                1));

        withAuth(() -> mockMvc.perform(get("/api/v1/notifications").param("nonLuesSeulement", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("CONGE_SOUMIS")));
        verify(notificationService).getMesNotifications(eq(USER_ID), eq(true), any());
    }

    @Test
    void marquerLu_ok() throws Exception {
        withAuth(() -> mockMvc.perform(put("/api/v1/notifications/{id}/lu", NOTIF_ID))
                .andExpect(status().isOk()));
        verify(notificationService).marquerLu(NOTIF_ID, USER_ID);
    }

    @Test
    void marquerToutLu_ok() throws Exception {
        withAuth(() -> mockMvc.perform(put("/api/v1/notifications/tout-lu"))
                .andExpect(status().isOk()));
        verify(notificationService).marquerToutLu(USER_ID);
    }

    @Test
    void compterNonLues_ok() throws Exception {
        when(notificationService.compterNonLues(USER_ID)).thenReturn(3L);
        withAuth(() -> mockMvc.perform(get("/api/v1/notifications/nb-non-lues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3)));
    }
}
