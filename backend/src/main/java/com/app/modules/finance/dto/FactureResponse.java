package com.app.modules.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record FactureResponse(
        UUID id,
        String reference,
        String fournisseur,
        LocalDate dateFacture,
        BigDecimal montantHt,
        BigDecimal tva,
        BigDecimal montantTtc,
        String devise,
        BigDecimal tauxChangeEur,
        BigDecimal montantTtcEur,
        UUID categorieId,
        String categorieLibelle,
        String statut,
        String justificatifUrl,
        BigDecimal montantPaye,
        BigDecimal montantRestant,
        LocalDateTime createdAt
) {}
