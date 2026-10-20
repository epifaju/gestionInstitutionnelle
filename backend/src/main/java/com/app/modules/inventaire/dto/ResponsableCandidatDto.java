package com.app.modules.inventaire.dto;

import java.util.UUID;

public record ResponsableCandidatDto(UUID id, String matricule, String nom, String prenom) {}
