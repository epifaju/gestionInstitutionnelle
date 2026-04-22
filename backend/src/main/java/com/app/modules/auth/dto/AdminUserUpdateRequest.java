package com.app.modules.auth.dto;

import com.app.modules.auth.entity.Role;
import jakarta.validation.constraints.Size;

/**
 * Mise à jour partielle : champs {@code null} = inchangés. Mot de passe vide = non modifié.
 */
public record AdminUserUpdateRequest(String nom, String prenom, Role role, Boolean actif, @Size(min = 8) String password) {}
