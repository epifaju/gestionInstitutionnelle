package com.app.modules.finance.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Totaux dépenses / recettes pour un mois calendaire (EUR). */
public record MoisMontantsDto(YearMonth mois, BigDecimal depenses, BigDecimal recettes) {}
