package com.app.modules.finance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PaiementLigneRequest(@NotNull UUID factureId, @NotNull @Positive BigDecimal montant) {}
