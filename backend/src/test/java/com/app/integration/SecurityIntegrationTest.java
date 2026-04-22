package com.app.integration;

import com.app.testutil.TestJwtKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration HTTP avec une base PostgreSQL éphémère (Testcontainers). Les migrations Flyway
 * PostgreSQL natives ne sont pas exécutables sur H2 ; un conteneur PostgreSQL reproduit la prod.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SecurityIntegrationTest {

    private static final String JWT_PRIV_B64;
    private static final String JWT_PUB_B64;

    static {
        try {
            var kp = TestJwtKeys.generateRsa2048();
            JWT_PRIV_B64 = TestJwtKeys.pemPrivateKeyB64Layer((RSAPrivateKey) kp.getPrivate());
            JWT_PUB_B64 = TestJwtKeys.pemPublicKeyB64Layer((RSAPublicKey) kp.getPublic());
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.jwt.private-key-b64", () -> JWT_PRIV_B64);
        r.add("app.jwt.public-key-b64", () -> JWT_PUB_B64);
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;

    @LocalServerPort private int port;

    @Test
    void testLoginFlow() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body =
                objectMapper.writeValueAsString(
                        Map.of("email", "admin@test.com", "password", "AdminTest123!"));
        ResponseEntity<String> res =
                restTemplate.exchange(
                        "/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = objectMapper.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isTrue();
        JsonNode data = root.path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
    }

    @Test
    void testAccessDenied() throws Exception {
        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setContentType(MediaType.APPLICATION_JSON);
        String loginBody =
                objectMapper.writeValueAsString(
                        Map.of("email", "fin@test.com", "password", "AdminTest123!"));
        ResponseEntity<String> loginRes =
                restTemplate.exchange(
                        "/api/v1/auth/login",
                        HttpMethod.POST,
                        new HttpEntity<>(loginBody, postHeaders),
                        String.class);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = objectMapper.readTree(loginRes.getBody()).path("data").path("accessToken").asText().trim();

        HttpClient httpClient = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port;

        HttpRequest rhReq =
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/v1/rh/salaries"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .GET()
                        .build();
        HttpResponse<String> rhRes = httpClient.send(rhReq, HttpResponse.BodyHandlers.ofString());
        assertThat(rhRes.statusCode()).isEqualTo(403);
    }
}
