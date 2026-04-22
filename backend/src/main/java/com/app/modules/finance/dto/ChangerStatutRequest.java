package com.app.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangerStatutRequest(@NotBlank String nouveauStatut) {}
