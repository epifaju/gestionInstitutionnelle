package com.app.modules.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String nom,
        String prenom,
        String role,
        boolean actif,
        Instant createdAt) {}
