package com.app.modules.auth.security;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Utilisateur;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Utilisateur utilisateur;
    private final Organisation organisation;

    public CustomUserDetails(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
        this.organisation = utilisateur.getOrganisation();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    @Override
    public String getPassword() {
        return utilisateur.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return utilisateur.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return utilisateur.isActif();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return utilisateur.isActif();
    }

    public UUID getId() {
        return utilisateur.getId();
    }

    public UUID getOrganisationId() {
        return utilisateur.getOrganisationId();
    }

    public String getOrganisationNom() {
        return organisation != null ? organisation.getNom() : null;
    }
}
