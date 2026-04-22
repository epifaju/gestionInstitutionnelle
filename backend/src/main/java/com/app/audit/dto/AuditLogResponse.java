package com.app.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        Instant dateAction,
        String action,
        String entite,
        UUID entiteId,
        JsonNode avant,
        JsonNode apres,
        UUID utilisateurId,
        String utilisateurEmail,
        String ipAddress,
        String userAgent) {}
