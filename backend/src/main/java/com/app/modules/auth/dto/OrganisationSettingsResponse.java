package com.app.modules.auth.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrganisationSettingsResponse(
        UUID id,
        String nom,
        String titreApp,
        String slogan,
        String type,
        String pays,
        String deviseDefaut,
        String logoUrl,
        BigDecimal seuilJustificatif,
        Integer alerteBudgetPct,
        boolean actif) {}
