package com.app.modules.templates.dto;

import com.app.modules.templates.service.TemplateOutputFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record GenerateTemplateRequest(
        @NotBlank String subjectType,
        @NotNull UUID subjectId,
        @NotNull TemplateOutputFormat outputFormat,
        Boolean saveToGed,
        String locale,
        Map<String, String> overrides
) {}

