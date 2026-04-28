package com.app.modules.templates.dto;

import java.time.Instant;
import java.util.UUID;

public record GeneratedDocumentResponse(
        UUID id,
        UUID templateRevisionId,
        String subjectType,
        UUID subjectId,
        UUID outputDocumentId,
        String outputFormat,
        UUID createdBy,
        Instant createdAt
) {}

