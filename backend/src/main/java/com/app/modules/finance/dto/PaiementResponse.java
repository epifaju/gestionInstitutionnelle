package com.app.modules.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaiementResponse(
        UUID id,
        LocalDate datePaiement,
        BigDecimal montantTotal,
        String devise,
        String compte,
        String moyenPaiement,
        String notes,
        LocalDateTime createdAt
) {}
