package com.app.modules.employe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EmployeCongeRequest(
        @NotBlank String typeConge,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        String commentaire
) {
}

