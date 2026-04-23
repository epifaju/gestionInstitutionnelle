package com.app.modules.auth.controller;

import com.app.modules.auth.dto.LoginRequest;
import com.app.modules.auth.dto.LoginResponse;
import com.app.modules.auth.dto.UpdateLangueRequest;
import com.app.modules.auth.dto.UserInfo;
import com.app.modules.auth.service.AuthService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AuthService authService = mock(AuthService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        // GlobalExceptionHandler requires an Environment; we can pass a mock since it is only used for the generic handler.
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new AuthController(authService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    @Test
    void login_ok_retourneApiResponseOk() throws Exception {
        UUID userId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID orgId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        UserInfo ui = new UserInfo(userId, "x@test.com", "Doe", "John", "ADMIN", orgId, "Org", "fr");
        when(authService.login(any(LoginRequest.class), any())).thenReturn(new LoginResponse("access", "Bearer", 900, ui));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("x@test.com", "NewPass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.user.email").value("x@test.com"));
    }

    @Test
    void updateLangue_ok_appelleService_etRetourneUserInfo() throws Exception {
        UUID userId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID orgId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        UserInfo ui = new UserInfo(userId, "x@test.com", null, null, "ADMIN", orgId, null, "pt-PT");
        when(authService.updateLangue("pt_pt")).thenReturn(ui);

        mockMvc.perform(put("/api/v1/auth/me/langue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateLangueRequest("pt_pt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.langue").value("pt-PT"));

        verify(authService).updateLangue(eq("pt_pt"));
    }

    @Test
    void updateLangue_blank_retourne400_validationError() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me/langue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"langue\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}

