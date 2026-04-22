package com.app.modules.auth.security;

import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @InjectMocks private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsername_absent_lanceUsernameNotFound() {
        when(utilisateurRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("x@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("x@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Utilisateur introuvable");
    }

    @Test
    void loadUserByUsername_ok_retourneUserDetails() {
        Utilisateur u = new Utilisateur();
        u.setId(UUID.fromString("a0000000-0000-0000-0000-000000000001"));
        u.setEmail("x@test.com");
        u.setRole(Role.ADMIN);
        u.setOrganisationId(UUID.fromString("b0000000-0000-0000-0000-000000000001"));
        when(utilisateurRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("x@test.com")).thenReturn(Optional.of(u));

        var details = customUserDetailsService.loadUserByUsername("x@test.com");
        assertThat(details.getUsername()).isEqualTo("x@test.com");
        assertThat(details.getAuthorities()).isNotEmpty();
    }
}

