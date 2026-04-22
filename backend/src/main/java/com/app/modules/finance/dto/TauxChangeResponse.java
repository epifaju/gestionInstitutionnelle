package com.app.modules.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TauxChangeResponse(
        LocalDate date,
        String devise,
        BigDecimal tauxVersEur
) {}

