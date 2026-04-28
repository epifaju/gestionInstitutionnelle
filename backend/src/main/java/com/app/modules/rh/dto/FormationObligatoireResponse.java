package com.app.modules.rh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record FormationObligatoireResponse(
        UUID id, UUID salarieId, String salarieNomComplet,
        String intitule, String typeFormation, String organisme,
        LocalDate dateRealisation, LocalDate dateExpiration,
        Integer periodiciteMois, String numeroCertificat,
        String certificatUrl, String statut,
        Integer joursAvantExpiration,
        BigDecimal cout, LocalDateTime createdAt
) {}

