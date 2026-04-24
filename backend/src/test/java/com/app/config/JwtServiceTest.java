package com.app.config;

import com.app.testutil.TestJwtKeys;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void generateAccessToken_puisValidateAndExtract_restitueClaims() throws Exception {
        KeyPair kp = TestJwtKeys.generateRsa2048();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();

        JwtProperties props = new JwtProperties();
        props.setAccessTokenMinutes(15);
        props.setRefreshTokenDays(7);

        JwtService jwtService = new JwtService(priv, pub, props);

        UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        String token = jwtService.generateAccessToken("admin@test.com", orgId, "ADMIN");

        Claims claims = jwtService.validateAndExtract(token);

        assertThat(jwtService.extractEmail(claims)).isEqualTo("admin@test.com");
        assertThat(jwtService.extractRole(claims)).isEqualTo("ADMIN");
        assertThat(jwtService.extractOrgId(claims)).isEqualTo(orgId);
        assertThat(jwtService.getAccessTokenExpiresInSeconds()).isEqualTo(15 * 60);
        assertThat(jwtService.getRefreshTokenDays()).isEqualTo(7);
    }

    @Test
    void hashToken_estDeterministe() throws Exception {
        KeyPair kp = TestJwtKeys.generateRsa2048();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        JwtService jwtService = new JwtService(priv, pub, new JwtProperties());

        String h1 = jwtService.hashToken("abc");
        String h2 = jwtService.hashToken("abc");
        String h3 = jwtService.hashToken("abcd");

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);
        assertThat(h1).hasSize(64); // SHA-256 hex
    }
}

