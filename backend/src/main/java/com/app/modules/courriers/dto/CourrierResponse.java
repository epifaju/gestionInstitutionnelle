package com.app.modules.courriers.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CourrierResponse(
        UUID id,
        String sens,
        String statut,
        String reference,
        String objet,
        String expediteur,
        String destinataire,
        LocalDate dateCourrier,
        LocalDate dateReception,
        LocalDate dateEnvoi,
        String canal,
        String notes,
        UUID documentId,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
