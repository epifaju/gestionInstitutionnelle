package com.app.modules.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecetteResponse(
        UUID id,
        LocalDate dateRecette,
        BigDecimal montant,
        String devise,
        BigDecimal tauxChangeEur,
        BigDecimal montantEur,
        String typeRecette,
        String description,
        String modeEncaissement,
        String justificatifUrl,
        UUID categorieId,
        String categorieLibelle,
        LocalDateTime createdAt
) {}
