package com.app.modules.missions.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FraisResponse(
        UUID id,
        String typeFrais,
        String description,
        LocalDate dateFrais,
        BigDecimal montant,
        String devise,
        BigDecimal montantEur,
        String justificatifUrl,
        String statut
) {}

