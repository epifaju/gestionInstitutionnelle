package com.app.modules.courriers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CourrierRequest(
        @NotBlank String sens,
        String statut,
        String reference,
        @NotBlank String objet,
        String expediteur,
        String destinataire,
        @NotNull LocalDate dateCourrier,
        LocalDate dateReception,
        LocalDate dateEnvoi,
        String canal,
        String notes,
        UUID documentId
) {}
