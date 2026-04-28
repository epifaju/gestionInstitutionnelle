package com.app.modules.templates.dto;

import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.entity.TemplateStatus;

import java.time.Instant;
import java.util.UUID;

public record TemplateDefinitionResponse(
        UUID id,
        String code,
        String label,
        TemplateCategory category,
        TemplateFormat format,
        TemplateStatus status,
        String defaultLocale,
        Instant createdAt,
        Instant updatedAt
) {}

