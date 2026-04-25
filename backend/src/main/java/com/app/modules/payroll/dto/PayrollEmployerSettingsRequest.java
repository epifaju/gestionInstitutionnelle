package com.app.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayrollEmployerSettingsRequest(
        @NotBlank @Size(max = 200) String raisonSociale,
        String adresseLigne1,
        String adresseLigne2,
        String codePostal,
        String ville,
        String pays,
        String siret,
        String naf,
        String urssaf,
        String conventionCode,
        String conventionLibelle
) {}

