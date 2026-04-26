package com.app.modules.ged.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String titre,
        String description,
        String typeDocument,
        String[] tags,
        String nomFichier,
        long tailleOctets,
        String mimeType,
        int version,
        UUID documentParentId,
        String visibilite,
        String serviceCible,
        String entiteLieeType,
        UUID entiteLieeId,
        LocalDate dateExpiration,
        String uploadeParNomComplet,
        LocalDateTime createdAt,
        String presignedUrl
) {}

