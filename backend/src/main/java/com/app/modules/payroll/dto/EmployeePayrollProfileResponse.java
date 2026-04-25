package com.app.modules.payroll.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EmployeePayrollProfileResponse(
        UUID id,
        UUID salarieId,
        String salarieNomComplet,
        boolean cadre,
        String conventionCode,
        String conventionLibelle,
        BigDecimal tauxPas
) {}

