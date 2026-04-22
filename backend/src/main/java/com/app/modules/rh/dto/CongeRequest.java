package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CongeRequest(
        @NotNull UUID salarieId,
        @NotBlank String typeConge,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        String commentaire
) {
}
