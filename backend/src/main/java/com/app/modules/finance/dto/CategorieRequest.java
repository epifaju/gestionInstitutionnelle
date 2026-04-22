package com.app.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategorieRequest(
        @NotBlank String libelle,
        @NotBlank String code,
        @NotNull String type,
        String couleur
) {}
