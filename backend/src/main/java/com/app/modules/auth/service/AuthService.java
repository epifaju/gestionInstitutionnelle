package com.app.modules.auth.service;

import com.app.config.JwtProperties;
import com.app.config.JwtService;
import com.app.modules.auth.dto.ForgotPasswordRequest;
import com.app.modules.auth.dto.LoginRequest;
import com.app.modules.auth.dto.LoginResponse;
import com.app.modules.auth.dto.MessageResponse;
import com.app.modules.auth.dto.RefreshResponse;
import com.app.modules.auth.dto.ResetPasswordRequest;
import com.app.modules.auth.dto.UpdatePasswordRequest;
import com.app.modules.auth.dto.UpdateProfileRequest;
import com.app.modules.auth.dto.UpdateProfileResponse;
import com.app.modules.auth.dto.UserInfo;
import com.app.modules.auth.dto.UserPreferencesRequest;
import com.app.modules.auth.dto.UserPreferencesResponse;
import com.app.modules.auth.entity.PasswordResetToken;
import com.app.modules.auth.entity.RefreshToken;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.PasswordResetTokenRepository;
import com.app.modules.auth.repository.RefreshTokenRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String MSG_FORGOT_GENERIC =
            "Si cet email correspond à un compte actif, vous pouvez réinitialiser votre mot de passe avec le jeton reçu.";

    private static final String REFRESH_COOKIE = "refreshToken";
    /** Path large enough pour /auth/refresh et /auth/logout (cookie HttpOnly). */
    private static final String AUTH_COOKIE_PATH = "/api/v1/auth";
    private static final long REFRESH_MAX_AGE_SECONDS = 604800L;

    private final UtilisateurRepository utilisateurRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    /** HTTPS : activer en prod pour le flag Secure du cookie refresh (PRD §7.5). */
    @Value("${app.security.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @PermitAll
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        Utilisateur utilisateur = utilisateurRepository
                .findFirstByEmailIgnoreCaseOrderByIdAsc(request.email())
                .orElseThrow(() -> BusinessException.badRequest("IDENTIFIANTS_INCORRECTS"));

        if (!utilisateur.isActif()
                || !passwordEncoder.matches(request.password(), utilisateur.getPasswordHash())) {
            throw BusinessException.badRequest("IDENTIFIANTS_INCORRECTS");
        }

        utilisateur.setDernierLogin(Instant.now());
        utilisateurRepository.save(utilisateur);

        String accessToken = jwtService.generateAccessToken(
                utilisateur.getEmail(),
                utilisateur.getOrganisationId(),
                utilisateur.getRole().name());

        String rawRefresh = jwtService.generateRefreshToken();
        String hash = jwtService.hashToken(rawRefresh);
        RefreshToken entity = new RefreshToken();
        entity.setUtilisateur(utilisateur);
        entity.setTokenHash(hash);
        entity.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenDays(), ChronoUnit.DAYS));
        entity.setUsed(false);
        entity.setCreatedAt(Instant.now());
        refreshTokenRepository.save(entity);

        appendRefreshCookie(response, rawRefresh);

        CustomUserDetails details = new CustomUserDetails(utilisateur);
        UserInfo userInfo = UserInfo.from(details);
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenExpiresInSeconds(),
                userInfo);
    }

    @PermitAll
    @Transactional
    public RefreshResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = readCookie(request, REFRESH_COOKIE);
        if (raw == null) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        String hash = jwtService.hashToken(raw);
        RefreshToken existing = refreshTokenRepository
                .findByTokenHashAndUsedFalseAndExpiresAtAfter(hash, Instant.now())
                .orElseThrow(() -> BusinessException.unauthorized("TOKEN_INVALIDE"));

        existing.setUsed(true);
        refreshTokenRepository.save(existing);

        Utilisateur utilisateur = existing.getUtilisateur();
        String accessToken = jwtService.generateAccessToken(
                utilisateur.getEmail(),
                utilisateur.getOrganisationId(),
                utilisateur.getRole().name());

        String newRaw = jwtService.generateRefreshToken();
        String newHash = jwtService.hashToken(newRaw);
        RefreshToken next = new RefreshToken();
        next.setUtilisateur(utilisateur);
        next.setTokenHash(newHash);
        next.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenDays(), ChronoUnit.DAYS));
        next.setUsed(false);
        next.setCreatedAt(Instant.now());
        refreshTokenRepository.save(next);

        appendRefreshCookie(response, newRaw);
        return new RefreshResponse(accessToken, jwtService.getAccessTokenExpiresInSeconds());
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String raw = readCookie(request, REFRESH_COOKIE);
        if (raw != null) {
            String hash = jwtService.hashToken(raw);
            refreshTokenRepository.findByTokenHashAndUsedFalseAndExpiresAtAfter(hash, Instant.now())
                    .ifPresent(token -> {
                        token.setUsed(true);
                        refreshTokenRepository.save(token);
                    });
        }
        clearRefreshCookie(response);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public UserInfo getMe() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomUserDetails details)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        return UserInfo.from(details);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public UserInfo updateLangue(String langue) {
        String l = langue == null ? "" : langue.trim();
        if (l.isEmpty()) {
            throw BusinessException.badRequest("LANGUE_INVALIDE");
        }
        // Normalisation minimale: fr / en / pt-PT
        l = l.equalsIgnoreCase("pt_pt") ? "pt-PT" : l;
        if (!("fr".equalsIgnoreCase(l) || "en".equalsIgnoreCase(l) || "pt-PT".equalsIgnoreCase(l))) {
            throw BusinessException.badRequest("LANGUE_INVALIDE");
        }

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomUserDetails details)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        UUID userId = details.getId();
        Utilisateur u =
                utilisateurRepository
                        .findById(userId)
                        .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND"));
        u.setLangue("pt-PT".equalsIgnoreCase(l) ? "pt-PT" : l.toLowerCase());
        utilisateurRepository.save(u);

        // Rafraîchir l'objet dans le UserDetails (utile pour /me sans relog)
        details.getUtilisateur().setLangue(u.getLangue());

        return UserInfo.from(details);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public UpdateProfileResponse updateProfile(UpdateProfileRequest req) {
        CustomUserDetails details = requireUserDetails();
        UUID userId = details.getId();

        Utilisateur u = utilisateurRepository
                .findById(userId)
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND"));

        String newEmail = req.email() == null ? "" : req.email().trim().toLowerCase();
        if (newEmail.isEmpty()) {
            throw BusinessException.badRequest("EMAIL_INVALIDE");
        }
        if (!newEmail.equalsIgnoreCase(u.getEmail())) {
            // email est utilisé comme subject JWT -> on doit assurer l'unicité globale
            var users = utilisateurRepository.findAllByEmailIgnoreCase(newEmail);
            boolean takenByOther = users.stream().anyMatch(x -> !x.getId().equals(u.getId()));
            if (takenByOther) {
                throw BusinessException.badRequest("EMAIL_DEJA_UTILISE");
            }
            u.setEmail(newEmail);
        }

        u.setNom(normalizeOptional(req.nom(), 100));
        u.setPrenom(normalizeOptional(req.prenom(), 100));
        utilisateurRepository.save(u);

        // rafraîchir userDetails (important si email changé)
        details.getUtilisateur().setEmail(u.getEmail());
        details.getUtilisateur().setNom(u.getNom());
        details.getUtilisateur().setPrenom(u.getPrenom());

        String newAccessToken = jwtService.generateAccessToken(
                u.getEmail(),
                details.getOrganisationId(),
                u.getRole().name()
        );
        long expiresInSeconds = jwtService.getAccessTokenExpiresInSeconds();

        return new UpdateProfileResponse(UserInfo.from(details), newAccessToken, expiresInSeconds);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public MessageResponse updatePassword(UpdatePasswordRequest req) {
        CustomUserDetails details = requireUserDetails();
        UUID userId = details.getId();

        Utilisateur u = utilisateurRepository
                .findById(userId)
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND"));

        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw BusinessException.badRequest("MOTDEPASSE_ACTUEL_INCORRECT");
        }
        if (req.newPassword() == null || req.newPassword().trim().length() < 8) {
            throw BusinessException.badRequest("MOTDEPASSE_INVALIDE");
        }

        u.setPasswordHash(passwordEncoder.encode(req.newPassword().trim()));
        utilisateurRepository.save(u);

        // invalider les refresh tokens existants (défense en profondeur)
        refreshTokenRepository.invalidateAllForUser(userId);
        return new MessageResponse("OK");
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public UserPreferencesResponse getPreferences() {
        CustomUserDetails details = requireUserDetails();
        Utilisateur u = utilisateurRepository
                .findById(details.getId())
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND"));
        return parsePreferences(u.getPreferences());
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public UserPreferencesResponse updatePreferences(UserPreferencesRequest req) {
        CustomUserDetails details = requireUserDetails();
        Utilisateur u = utilisateurRepository
                .findById(details.getId())
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND"));

        String theme = normalizeTheme(req.theme());
        var uiEnabled = sanitizeNotificationTypes(req.notificationsUiEnabled());
        var emailEnabled = sanitizeNotificationTypes(req.notificationsEmailEnabled());

        ObjectNode root = objectMapper.createObjectNode();
        root.put("theme", theme);
        ObjectNode notifs = root.putObject("notifications");
        ArrayNode uiArr = notifs.putArray("uiEnabled");
        uiEnabled.forEach(uiArr::add);
        ArrayNode emailArr = notifs.putArray("emailEnabled");
        emailEnabled.forEach(emailArr::add);

        u.setPreferences(root.toString());
        utilisateurRepository.save(u);

        return new UserPreferencesResponse(theme, uiEnabled, emailEnabled);
    }

    private CustomUserDetails requireUserDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomUserDetails details)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        return details;
    }

    private static String normalizeOptional(String v, int maxLen) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty()) return null;
        return t.length() > maxLen ? t.substring(0, maxLen) : t;
    }

    private String normalizeTheme(String theme) {
        String t = theme == null ? "" : theme.trim().toLowerCase();
        return switch (t) {
            case "light", "dark", "system" -> t;
            default -> throw BusinessException.badRequest("THEME_INVALIDE");
        };
    }

    private java.util.List<String> sanitizeNotificationTypes(java.util.List<String> raw) {
        if (raw == null) return java.util.List.of();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String s : raw) {
            if (s == null) continue;
            String v = s.trim();
            if (v.isEmpty()) continue;
            // validation : doit être un NotificationType existant
            try {
                com.app.modules.notifications.entity.NotificationType.valueOf(v);
            } catch (Exception ex) {
                throw BusinessException.badRequest("NOTIFICATION_TYPE_INVALIDE");
            }
            if (!out.contains(v)) out.add(v);
        }
        return java.util.List.copyOf(out);
    }

    private UserPreferencesResponse parsePreferences(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new UserPreferencesResponse("system", java.util.List.of(), java.util.List.of());
            }
            JsonNode root = objectMapper.readTree(json);
            String theme = root.path("theme").asText("system");
            if (!("light".equals(theme) || "dark".equals(theme) || "system".equals(theme))) theme = "system";
            java.util.List<String> ui = readStringArray(root.path("notifications").path("uiEnabled"));
            java.util.List<String> email = readStringArray(root.path("notifications").path("emailEnabled"));
            return new UserPreferencesResponse(theme, ui, email);
        } catch (Exception ex) {
            return new UserPreferencesResponse("system", java.util.List.of(), java.util.List.of());
        }
    }

    private static java.util.List<String> readStringArray(JsonNode n) {
        if (n == null || !n.isArray()) return java.util.List.of();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (JsonNode it : n) {
            if (it != null && it.isTextual()) {
                String v = it.asText().trim();
                if (!v.isEmpty()) out.add(v);
            }
        }
        return java.util.List.copyOf(out);
    }

    @PermitAll
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest req) {
        String email = req.email().trim().toLowerCase();
        List<Utilisateur> users = utilisateurRepository.findAllByEmailIgnoreCase(email);
        if (users.size() != 1) {
            return new MessageResponse(MSG_FORGOT_GENERIC);
        }
        Utilisateur u = users.get(0);
        if (!u.isActif()) {
            return new MessageResponse(MSG_FORGOT_GENERIC);
        }
        String raw = UUID.randomUUID() + UUID.randomUUID().toString();
        String hash = jwtService.hashToken(raw);
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUtilisateur(u);
        prt.setTokenHash(hash);
        prt.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        prt.setUsed(false);
        prt.setCreatedAt(Instant.now());
        passwordResetTokenRepository.save(prt);
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            log.info("[DEV] Réinitialisation MDP pour {} — coller le jeton sur /reset-password : {}", email, raw);
        }
        return new MessageResponse(MSG_FORGOT_GENERIC);
    }

    @PermitAll
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest req) {
        String hash = jwtService.hashToken(req.token().trim());
        PasswordResetToken prt =
                passwordResetTokenRepository
                        .findByTokenHashAndUsedFalseAndExpiresAtAfter(hash, Instant.now())
                        .orElseThrow(() -> BusinessException.badRequest("RESET_TOKEN_INVALIDE"));
        Utilisateur u = prt.getUtilisateur();
        prt.setUsed(true);
        passwordResetTokenRepository.save(prt);
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        utilisateurRepository.save(u);
        return new MessageResponse("Mot de passe mis à jour. Vous pouvez vous connecter.");
    }

    private void appendRefreshCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, rawToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path(AUTH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(REFRESH_MAX_AGE_SECONDS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path(AUTH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
