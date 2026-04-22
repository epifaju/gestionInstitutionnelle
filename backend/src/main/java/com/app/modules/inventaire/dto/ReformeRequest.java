package com.app.modules.inventaire.dto;

import jakarta.validation.constraints.NotBlank;

public record ReformeRequest(@NotBlank String motif) {}
