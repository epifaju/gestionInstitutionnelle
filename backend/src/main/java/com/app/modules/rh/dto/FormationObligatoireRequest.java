package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FormationObligatoireRequest(
        @NotBlank String intitule,
        @NotBlank String typeFormation,
        String organisme,
        LocalDate dateRealisation,
        @NotNull LocalDate dateExpiration,
        Integer periodiciteMois,
        String numeroCertificat,
        BigDecimal cout
) {}

