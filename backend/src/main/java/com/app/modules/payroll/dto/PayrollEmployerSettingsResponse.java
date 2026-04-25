package com.app.modules.payroll.dto;

import java.util.UUID;

public record PayrollEmployerSettingsResponse(
        UUID organisationId,
        String raisonSociale,
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

