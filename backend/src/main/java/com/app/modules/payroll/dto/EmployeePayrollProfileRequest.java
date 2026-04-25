package com.app.modules.payroll.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record EmployeePayrollProfileRequest(
        @NotNull UUID salarieId,
        boolean cadre,
        String conventionCode,
        String conventionLibelle,
        BigDecimal tauxPas
) {}

