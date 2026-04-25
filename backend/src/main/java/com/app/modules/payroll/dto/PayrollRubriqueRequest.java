package com.app.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayrollRubriqueRequest(
        @NotBlank String code,
        @NotBlank String libelle,
        @NotBlank String type,
        @NotBlank String modeCalcul,
        String baseCode,
        BigDecimal tauxSalarial,
        BigDecimal tauxPatronal,
        BigDecimal montantFixe,
        Integer ordreAffichage,
        boolean actif,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}

