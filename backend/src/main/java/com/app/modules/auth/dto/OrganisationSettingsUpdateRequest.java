package com.app.modules.auth.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrganisationSettingsUpdateRequest(
        @NotBlank @Size(max = 200) String nom,
        @Size(max = 120) String titreApp,
        @Size(max = 200) String slogan,
        @NotBlank @Size(max = 50) String type,
        @Size(max = 100) String pays,
        @NotBlank @Size(min = 3, max = 3) String deviseDefaut,
        @Size(max = 500) String logoUrl,
        @DecimalMin("0") BigDecimal seuilJustificatif,
        @Min(1) @Max(100) Integer alerteBudgetPct) {}
