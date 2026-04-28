package com.app.modules.rh.dto;

public record ConfigAlerteRequest(
        Integer alerteFinCddJ, Integer alertePeriodeEssaiJ,
        Integer alerteVisiteMedJ, Integer alerteTitreSejourJ,
        Integer alerteFormationJ,
        boolean notifierRh, boolean notifierManager, boolean notifierSalarie,
        Integer maxRenouvellementsCdd
) {}

