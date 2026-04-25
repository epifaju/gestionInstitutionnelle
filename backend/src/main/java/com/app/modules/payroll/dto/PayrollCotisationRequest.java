package com.app.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayrollCotisationRequest(
        @NotBlank String code,
        @NotBlank String libelle,
        String organisme,
        @NotBlank String assietteBaseCode,
        BigDecimal tauxSalarial,
        BigDecimal tauxPatronal,
        String plafondCode,
        boolean appliesCadreOnly,
        boolean appliesNonCadreOnly,
        Integer ordreAffichage,
        boolean actif,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}

