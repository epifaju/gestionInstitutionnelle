package com.app.modules.templates.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TemplateCategory {
    MISSION,
    FRAIS,
    CONTRAT,
    COURRIER,
    PV

    // Backward compatibility for older payloads / stored values
    ;

    @JsonCreator
    public static TemplateCategory from(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "MISSION", "MISSION_TYPE" -> MISSION;
            case "FRAIS", "FRAIS_TYPE" -> FRAIS;
            case "CONTRAT", "TYPE_CONTRAT" -> CONTRAT;
            case "COURRIER" -> COURRIER;
            case "PV" -> PV;
            default -> TemplateCategory.valueOf(v);
        };
    }
}

