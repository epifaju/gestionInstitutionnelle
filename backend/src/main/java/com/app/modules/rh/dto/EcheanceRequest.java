package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EcheanceRequest(
        @NotNull UUID salarieId,
        UUID contratId,                      // optionnel
        @NotBlank String typeEcheance,
        @NotBlank String titre,
        String description,
        @NotNull LocalDate dateEcheance,
        Integer priorite,                    // 1-3, défaut 2
        UUID responsableId
) {}

