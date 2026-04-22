package com.app.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateLangueRequest(@NotBlank String langue) {}

