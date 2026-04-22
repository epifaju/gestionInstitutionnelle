package com.app.modules.finance.dto;

import java.util.UUID;

public record CategorieResponse(UUID id, String libelle, String code, String type, String couleur, boolean actif) {}
