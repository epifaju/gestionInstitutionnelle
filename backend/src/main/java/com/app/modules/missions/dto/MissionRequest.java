package com.app.modules.missions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MissionRequest(
        @NotBlank String titre,
        @NotBlank String destination,
        String paysDestination,
        String objectif,
        @NotNull LocalDate dateDepart,
        @NotNull LocalDate dateRetour,
        BigDecimal avanceDemandee,
        String avanceDevise
) {}

