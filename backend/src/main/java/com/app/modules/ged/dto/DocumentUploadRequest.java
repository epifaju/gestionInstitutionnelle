package com.app.modules.ged.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentUploadRequest(
        @NotBlank String titre,
        String description,
        @NotBlank String typeDocument,
        String[] tags,
        String visibilite,
        String serviceCible,
        String entiteLieeType,
        UUID entiteLieeId,
        LocalDate dateExpiration,
        UUID documentParentId
) {}

