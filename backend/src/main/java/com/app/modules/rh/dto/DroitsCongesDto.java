package com.app.modules.rh.dto;

import java.math.BigDecimal;

public record DroitsCongesDto(
        int annee,
        BigDecimal joursDroit,
        BigDecimal joursPris,
        BigDecimal joursRestants
) {
}
