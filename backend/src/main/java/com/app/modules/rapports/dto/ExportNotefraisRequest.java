package com.app.modules.rapports.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExportNotefraisRequest(@NotNull UUID missionId) {}

