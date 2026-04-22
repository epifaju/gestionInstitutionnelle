package com.app.modules.auth.service;

import com.app.config.JwtProperties;
import com.app.config.JwtService;
import com.app.modules.auth.dto.ForgotPasswordRequest;
import com.app.modules.auth.dto.LoginRequest;
import com.app.modules.auth.dto.ResetPasswordRequest;
import com.app.modules.auth.entity.PasswordResetToken;
import com.app.modules.auth.entity.RefreshToken;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.PasswordResetTokenRepository;
import com.app.modules.auth.repository.RefreshTokenRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.shared.exception.BusinessException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;
    @Mock private Environment environment;

    @InjectMocks private AuthService authService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    void login_identifiantsInvalides_refuse_siUserAbsent() {
        when(utilisateurRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("x@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@test.com", "bad"), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "IDENTIFIANTS_INCORRECTS");
    }

    @Test
    void login_identifiantsInvalides_refuse_siInactif() {
        Utilisateur u = new Utilisateur();
        u.setOrganisationId(orgId);
        u.setEmail("x@test.com");
        u.setRole(Role.ADMIN);
        u.setActif(false);
        u.setPasswordHash("hash");
        when(utilisateurRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("x@test.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@test.com", "pw"), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "IDENTIFIANTS_INCORRECTS");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_ok_poseCookieRefresh_etRetourneAccessToken() {
        Utilisateur u = new Utilisateur();
        u.setOrganisationId(orgId);
        u.setEmail("admin@test.com");
        u.setRole(Role.ADMIN);
        u.setActif(true);
        u.setPasswordHash("hash");
        when(utilisateurRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("admin@test.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(eq("admin@test.com"), eq(orgId), eq("ADMIN"))).thenReturn("access");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-raw");
        when(jwtService.hashToken("refresh-raw")).thenReturn("refresh-hash");
        when(jwtProperties.getRefreshTokenDays()).thenReturn(7);
        when(jwtService.getAccessTokenExpiresInSeconds()).thenReturn(900);

        MockHttpServletResponse res = new MockHttpServletResponse();
        var out = authService.login(new LoginRequest("admin@test.com", "pw"), res);

        assertThat(out.accessToken()).isEqualTo("access");
        assertThat(res.getHeaders("Set-Cookie")).isNotEmpty();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refresh_sansCookie_refuse() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.refresh(req, res))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TOKEN_INVALIDE");
    }

    @Test
    void refresh_cookieInvalide_refuse() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("refreshToken", "raw"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtService.hashToken("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashAndUsedFalseAndExpiresAtAfter(eq("hash"), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(req, res))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TOKEN_INVALIDE");
    }

    @Test
    void forgotPassword_si0ouPlusieursUsers_retourneMessageGenerique() {
        when(utilisateurRepository.findAllByEmailIgnoreCase("x@test.com")).thenReturn(List.of());

        var res = authService.forgotPassword(new ForgotPasswordRequest("x@test.com"));
        assertThat(res.message()).contains("Si cet email");
    }

    @Test
    void resetPassword_tokenInvalide_refuse() {
        when(jwtService.hashToken("tok")).thenReturn("hash");
        when(passwordResetTokenRepository.findByTokenHashAndUsedFalseAndExpiresAtAfter(eq("hash"), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("tok", "NewPass123!")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "RESET_TOKEN_INVALIDE");
    }

    @Test
    void resetPassword_ok_marqueTokenUsed_etUpdatePassword() {
        Utilisateur u = new Utilisateur();
        u.setOrganisationId(orgId);
        u.setEmail("x@test.com");
        u.setActif(true);
        u.setRole(Role.ADMIN);

        PasswordResetToken prt = new PasswordResetToken();
        prt.setUtilisateur(u);
        prt.setTokenHash("hash");
        prt.setUsed(false);
        prt.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(jwtService.hashToken("tok")).thenReturn("hash");
        when(passwordResetTokenRepository.findByTokenHashAndUsedFalseAndExpiresAtAfter(eq("hash"), any()))
                .thenReturn(Optional.of(prt));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("encoded");

        var res = authService.resetPassword(new ResetPasswordRequest("tok", "NewPass123!"));

        assertThat(res.message()).contains("Mot de passe");
        verify(passwordResetTokenRepository).save(prt);
        verify(utilisateurRepository).save(u);
        assertThat(u.getPasswordHash()).isEqualTo("encoded");
    }
}

