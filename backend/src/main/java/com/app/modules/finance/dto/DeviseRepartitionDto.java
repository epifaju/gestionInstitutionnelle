package com.app.modules.finance.dto;

import java.math.BigDecimal;

public record DeviseRepartitionDto(
        String devise,
        BigDecimal montantOriginal,
        BigDecimal montantEur
) {}

