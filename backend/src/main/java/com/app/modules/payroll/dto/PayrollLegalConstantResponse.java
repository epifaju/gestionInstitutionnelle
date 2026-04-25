package com.app.modules.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollLegalConstantResponse(
        UUID id,
        String code,
        String libelle,
        BigDecimal valeur,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}

