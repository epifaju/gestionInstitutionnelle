package com.app.modules.templates.dto;

import java.time.Instant;
import java.util.UUID;

public record TemplateRevisionResponse(
        UUID id,
        int version,
        UUID contentDocumentId,
        String contentObjectName,
        String contentMime,
        String checksum,
        String comment,
        UUID createdBy,
        Instant createdAt
) {}

