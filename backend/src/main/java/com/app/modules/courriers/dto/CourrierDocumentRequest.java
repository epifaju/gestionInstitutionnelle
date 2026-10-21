package com.app.modules.courriers.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CourrierDocumentRequest(@NotNull UUID documentId) {}
