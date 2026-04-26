package com.app.modules.auth.dto;

public record UpdateProfileResponse(
        UserInfo user,
        String accessToken,
        long expiresInSeconds
) {}

