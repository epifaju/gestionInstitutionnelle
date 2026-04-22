package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;

public record CongeValidationRequest(@NotBlank String motifRejet) {
}
