package com.app.modules.auth.controller;

import com.app.modules.auth.dto.AdminUserCreateRequest;
import com.app.modules.auth.dto.AdminUserResponse;
import com.app.modules.auth.dto.AdminUserUpdateRequest;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.auth.service.AdminService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerMvcTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    private final AdminService adminService = mock(AdminService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new AdminController(adminService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(
                                new AuthenticationPrincipalArgumentResolver(),
                                new PageableHandlerMethodArgumentResolver())
                        .build();
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        Organisation o = new Organisation();
        o.setId(orgId);
        o.setNom("Org");
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setOrganisation(o);
        u.setEmail("admin@test.com");
        u.setRole(Role.ADMIN);
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
    void listUsers_ok_retournePageResponse() throws Exception {
        UUID id = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        AdminUserResponse row = new AdminUserResponse(id, "x@test.com", "Doe", "John", "EMPLOYE", true, Instant.now());
        var page = new org.springframework.data.domain.PageImpl<>(List.of(row), org.springframework.data.domain.PageRequest.of(0, 20), 1);
        when(adminService.listUsers(any(), any())).thenReturn(page);

        withAuth(adminAuth(), () -> mockMvc.perform(get("/api/v1/admin/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].email").value("x@test.com"))
                .andExpect(jsonPath("$.data.totalElements").value(1)));
    }

    @Test
    void createUser_ok_retourneUser() throws Exception {
        UUID id = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        AdminUserResponse created = new AdminUserResponse(id, "new@test.com", null, null, "RH", true, Instant.now());
        when(adminService.createUser(any(AdminUserCreateRequest.class), any(), any())).thenReturn(created);

        String body = """
                {"email":"new@test.com","password":"NewPass123!","nom":null,"prenom":null,"role":"RH"}
                """;

        withAuth(adminAuth(), () -> mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new@test.com"))
                .andExpect(jsonPath("$.data.role").value("RH")));
    }

    @Test
    void updateUser_ok_appelleService() throws Exception {
        UUID id = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        AdminUserResponse updated = new AdminUserResponse(id, "x@test.com", "Doe", "Jane", "EMPLOYE", true, Instant.now());
        when(adminService.updateUser(eq(id), any(AdminUserUpdateRequest.class), any(), any())).thenReturn(updated);

        String body = """
                {"nom":"Doe","prenom":"Jane","role":"EMPLOYE","actif":true,"password":null}
                """;

        withAuth(adminAuth(), () -> mockMvc.perform(put("/api/v1/admin/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.prenom").value("Jane")));

        verify(adminService).updateUser(eq(id), any(AdminUserUpdateRequest.class), any(), any());
    }
}

