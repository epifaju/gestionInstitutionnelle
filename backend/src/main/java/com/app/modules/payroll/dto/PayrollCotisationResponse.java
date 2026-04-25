package com.app.modules.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollCotisationResponse(
        UUID id,
        String code,
        String libelle,
        String organisme,
        String assietteBaseCode,
        BigDecimal tauxSalarial,
        BigDecimal tauxPatronal,
        String plafondCode,
        boolean appliesCadreOnly,
        boolean appliesNonCadreOnly,
        Integer ordreAffichage,
        boolean actif,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}

