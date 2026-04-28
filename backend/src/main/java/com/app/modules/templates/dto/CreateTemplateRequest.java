package com.app.modules.templates.dto;

import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTemplateRequest(
        @NotBlank String code,
        @NotBlank String label,
        @NotNull TemplateCategory category,
        @NotNull TemplateFormat format,
        String defaultLocale
) {}

