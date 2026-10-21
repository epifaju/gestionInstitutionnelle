package com.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void authenticationEntryPoint_retourne401Json() throws Exception {
        JsonAuthenticationEntryPoint ep = new JsonAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/secure");
        MockHttpServletResponse res = new MockHttpServletResponse();

        ep.commence(req, res, new BadCredentialsException("nope"));

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(res.getContentType()).contains("application/json");
        assertThat(res.getContentAsString()).contains("TOKEN_INVALIDE");
    }

    @Test
    void accessDeniedHandler_retourne403Json() throws Exception {
        JsonAccessDeniedHandler h = new JsonAccessDeniedHandler(objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin");
        MockHttpServletResponse res = new MockHttpServletResponse();

        h.handle(req, res, new AccessDeniedException("denied"));

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(res.getContentType()).contains("application/json");
        assertThat(res.getContentAsString()).contains("ACCES_REFUSE");
    }
}
