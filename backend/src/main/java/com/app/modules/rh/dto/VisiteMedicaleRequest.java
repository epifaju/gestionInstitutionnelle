package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record VisiteMedicaleRequest(
        @NotBlank String typeVisite,
        LocalDate datePlanifiee,
        LocalDate dateRealisee,
        String medecin, String centreMedical,
        @NotBlank String resultat,
        String restrictions,
        Integer periodiciteMois
) {}

