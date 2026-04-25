package com.app.modules.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollRubriqueResponse(
        UUID id,
        String code,
        String libelle,
        String type,
        String modeCalcul,
        String baseCode,
        BigDecimal tauxSalarial,
        BigDecimal tauxPatronal,
        BigDecimal montantFixe,
        Integer ordreAffichage,
        boolean actif,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}

