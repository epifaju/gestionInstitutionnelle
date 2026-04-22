package com.app.modules.rh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalaireActuel(
        BigDecimal montantBrut,
        BigDecimal montantNet,
        String devise,
        LocalDate dateDebut
) {
}
