package com.app.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogAdminResponse(
        UUID id,
        String utilisateurEmail,
        String utilisateurRole,
        String action,
        String entite,
        UUID entiteId,
        JsonNode avant,
        JsonNode apres,
        String ipAddress,
        LocalDateTime dateAction
) {}

