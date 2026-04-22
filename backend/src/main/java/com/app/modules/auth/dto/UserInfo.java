package com.app.modules.auth.dto;

import com.app.modules.auth.security.CustomUserDetails;

import java.util.UUID;

public record UserInfo(
        UUID id,
        String email,
        String nom,
        String prenom,
        String role,
        UUID organisationId,
        String organisationNom,
        String langue
) {
    public static UserInfo from(CustomUserDetails user) {
        return new UserInfo(
                user.getId(),
                user.getUtilisateur().getEmail(),
                user.getUtilisateur().getNom(),
                user.getUtilisateur().getPrenom(),
                user.getUtilisateur().getRole().name(),
                user.getOrganisationId(),
                user.getOrganisationNom(),
                user.getUtilisateur().getLangue()
        );
    }
}
