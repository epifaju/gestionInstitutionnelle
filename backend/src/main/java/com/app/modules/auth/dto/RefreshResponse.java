package com.app.modules.auth.dto;

public record RefreshResponse(
        String accessToken,
        long expiresIn
) {
}
