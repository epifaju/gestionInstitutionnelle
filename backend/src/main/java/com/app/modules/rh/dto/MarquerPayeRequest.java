package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MarquerPayeRequest(
        @NotNull LocalDate datePaiement,
        @NotBlank String modePaiement,
        String notes
) {
}
