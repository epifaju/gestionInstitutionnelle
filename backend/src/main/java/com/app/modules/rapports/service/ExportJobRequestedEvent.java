package com.app.modules.rapports.service;

import com.app.modules.rapports.entity.TypeExport;

import java.util.Map;
import java.util.UUID;

public record ExportJobRequestedEvent(
        UUID jobId,
        UUID organisationId,
        UUID userId,
        TypeExport typeExport,
        Map<String, Object> parametres
) {}

