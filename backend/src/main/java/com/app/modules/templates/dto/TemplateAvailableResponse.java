package com.app.modules.templates.dto;

import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.service.TemplateOutputFormat;

import java.util.List;

public record TemplateAvailableResponse(
        String code,
        String label,
        TemplateCategory category,
        TemplateFormat format,
        List<TemplateOutputFormat> allowedOutputs,
        boolean hasRevision,
        Integer latestVersion
) {}

