package com.app.modules.courriers.dto;

import jakarta.validation.constraints.NotBlank;

public record CourrierStatutRequest(@NotBlank String statut) {}
