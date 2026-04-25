package com.app.modules.missions.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MissionResponse(
        UUID id,
        String titre,
        String destination,
        String paysDestination,
        String objectif,
        LocalDate dateDepart,
        LocalDate dateRetour,
        int nbJours,
        String statut,
        String salarieNomComplet,
        BigDecimal avanceDemandee,
        BigDecimal avanceVersee,
        BigDecimal totalFraisValides,
        BigDecimal soldeARegler,
        String ordreMissionUrl,
        List<FraisResponse> frais,
        LocalDateTime createdAt
) {}

