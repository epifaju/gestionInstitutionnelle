package com.app.modules.templates.dto;

import com.app.modules.templates.entity.TemplateStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTemplateRequest(
        @NotBlank String label,
        @NotNull TemplateStatus status,
        String defaultLocale
) {}

