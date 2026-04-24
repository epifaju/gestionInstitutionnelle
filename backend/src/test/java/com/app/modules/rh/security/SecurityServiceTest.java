package com.app.modules.rh.security;

import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.SalarieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock private SalarieRepository salarieRepository;
    @Mock private Authentication authentication;

    @InjectMocks private SecurityService securityService;

    @Test
    void isSelf_retourneFalse_siAuthenticationNullOuPrincipalInvalide() {
        assertThat(securityService.isSelf(UUID.randomUUID(), null)).isFalse();

        when(authentication.getPrincipal()).thenReturn("not-a-user-details");
        assertThat(securityService.isSelf(UUID.randomUUID(), authentication)).isFalse();
    }

    @Test
    void isSelf_retourneFalse_siSalarieIntrouvable() {
        UUID salarieId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        Utilisateur u = new Utilisateur();
        u.setId(UUID.fromString("b0000000-0000-0000-0000-000000000001"));
        u.setRole(Role.EMPLOYE);
        u.setEmail("e@test.com");
        u.setPasswordHash("x");
        u.setActif(true);
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(u));
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.empty());

        assertThat(securityService.isSelf(salarieId, authentication)).isFalse();
    }

    @Test
    void isSelf_retourneTrue_siUtilisateurCorrespond() {
        UUID salarieId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b1000000-0000-0000-0000-000000000001");

        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setRole(Role.EMPLOYE);
        u.setEmail("e@test.com");
        u.setPasswordHash("x");
        u.setActif(true);
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(u));

        Salarie s = new Salarie();
        s.setId(salarieId);
        Utilisateur linked = new Utilisateur();
        linked.setId(userId);
        linked.setRole(Role.EMPLOYE);
        linked.setEmail("linked@test.com");
        linked.setPasswordHash("x");
        linked.setActif(true);
        s.setUtilisateur(linked);
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));

        assertThat(securityService.isSelf(salarieId, authentication)).isTrue();
    }

    @Test
    void isSelf_retourneFalse_siSalarieSansUtilisateurOuAutreUtilisateur() {
        UUID salarieId = UUID.fromString("a2000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b2000000-0000-0000-0000-000000000001");

        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setRole(Role.EMPLOYE);
        u.setEmail("e@test.com");
        u.setPasswordHash("x");
        u.setActif(true);
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(u));

        Salarie s1 = new Salarie();
        s1.setId(salarieId);
        s1.setUtilisateur(null);
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s1));
        assertThat(securityService.isSelf(salarieId, authentication)).isFalse();

        Salarie s2 = new Salarie();
        s2.setId(salarieId);
        Utilisateur other = new Utilisateur();
        other.setId(UUID.fromString("c2000000-0000-0000-0000-000000000001"));
        other.setRole(Role.EMPLOYE);
        other.setEmail("other@test.com");
        other.setPasswordHash("x");
        other.setActif(true);
        s2.setUtilisateur(other);
        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s2));
        assertThat(securityService.isSelf(salarieId, authentication)).isFalse();
    }
}

