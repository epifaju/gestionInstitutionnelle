package com.app.modules.workflow.dto;

import jakarta.validation.constraints.Size;

public record WorkflowDecisionRequest(
        @Size(max = 2000) String comment
) {}

