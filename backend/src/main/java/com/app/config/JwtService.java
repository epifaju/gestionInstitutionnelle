package com.app.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final RSAPrivateKey jwtPrivateKey;
    private final RSAPublicKey jwtPublicKey;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(String email, UUID orgId, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(jwtProperties.getAccessTokenMinutes() * 60L);
        return Jwts.builder()
                .subject(email)
                .claim("orgId", orgId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(jwtPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public String hashToken(String token) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(jwtPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public UUID extractOrgId(Claims claims) {
        String raw = claims.get("orgId", String.class);
        return raw != null ? UUID.fromString(raw) : null;
    }

    public int getAccessTokenExpiresInSeconds() {
        return jwtProperties.getAccessTokenMinutes() * 60;
    }

    public int getRefreshTokenDays() {
        return jwtProperties.getRefreshTokenDays();
    }
}
