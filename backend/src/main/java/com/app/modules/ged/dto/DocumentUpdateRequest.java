package com.app.modules.ged.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentUpdateRequest(
        @NotBlank String titre,
        String description,
        String[] tags,
        String visibilite,
        String serviceCible,
        String entiteLieeType,
        UUID entiteLieeId,
        LocalDate dateExpiration
) {}

