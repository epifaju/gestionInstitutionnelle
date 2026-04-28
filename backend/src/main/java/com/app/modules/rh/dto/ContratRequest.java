package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ContratRequest(
        @NotBlank String typeContrat,
        @NotNull LocalDate dateDebutContrat,
        LocalDate dateFinContrat,            // null si CDI
        LocalDate dateFinPeriodeEssai,
        Integer dureeEssaiMois,
        String numeroContrat,
        String intitulePoste,
        String motifCdd,                     // obligatoire si typeContrat=CDD
        String conventionCollective
) {}

