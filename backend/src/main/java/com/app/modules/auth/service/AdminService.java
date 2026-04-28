package com.app.modules.auth.service;

import com.app.audit.AuditLogRepository;
import com.app.audit.dto.AuditLogResponse;
import com.app.audit.entity.AuditLog;
import com.app.modules.auth.dto.AdminUserCreateRequest;
import com.app.modules.auth.dto.AdminUserResponse;
import com.app.modules.auth.dto.AdminUserUpdateRequest;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.audit.AuditLogService;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UtilisateurRepository utilisateurRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    /** Lecture seule ; aligné sur AdminController (RH = listes type modal échéances). */
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(UUID organisationId, Pageable pageable) {
        return utilisateurRepository
                .findByOrganisationIdOrderByEmailAsc(organisationId, pageable)
                .map(this::toUserResponse);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminUserResponse createUser(AdminUserCreateRequest req, UUID organisationId, UUID actorId) {
        String email = req.email().trim().toLowerCase();
        if (utilisateurRepository.existsByOrganisationIdAndEmailIgnoreCase(organisationId, email)) {
            throw BusinessException.badRequest("EMAIL_DEJA_UTILISE");
        }
        Utilisateur u = new Utilisateur();
        u.setOrganisationId(organisationId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setNom(trimToNull(req.nom()));
        u.setPrenom(trimToNull(req.prenom()));
        u.setRole(req.role());
        u.setActif(true);
        u = utilisateurRepository.save(u);

        auditLogService.log(
                organisationId,
                actorId,
                "CREATE",
                "Utilisateur",
                u.getId(),
                null,
                snapshotUser(u));
        return toUserResponse(u);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminUserResponse updateUser(UUID id, AdminUserUpdateRequest req, UUID organisationId, UUID actorId) {
        Utilisateur u =
                utilisateurRepository
                        .findByIdAndOrganisationId(id, organisationId)
                        .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND"));

        boolean hasPassword = req.password() != null && !req.password().isBlank();
        boolean anyPatch =
                req.nom() != null
                        || req.prenom() != null
                        || req.role() != null
                        || req.actif() != null
                        || hasPassword;
        if (!anyPatch) {
            return toUserResponse(u);
        }

        Map<String, Object> avant = snapshotUser(u);

        if (Boolean.FALSE.equals(req.actif()) && id.equals(actorId)) {
            throw BusinessException.badRequest("USER_SELF_DEACTIVATE");
        }
        guardLastAdmin(u, req);

        if (req.nom() != null) {
            u.setNom(trimToNull(req.nom()));
        }
        if (req.prenom() != null) {
            u.setPrenom(trimToNull(req.prenom()));
        }
        if (req.role() != null) {
            u.setRole(req.role());
        }
        if (req.actif() != null) {
            u.setActif(req.actif());
        }
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(req.password()));
        }

        u = utilisateurRepository.save(u);
        auditLogService.log(
                organisationId, actorId, "UPDATE", "Utilisateur", u.getId(), avant, snapshotUser(u));
        return toUserResponse(u);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listAuditLogs(UUID organisationId, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findByOrganisationIdOrderByDateActionDesc(organisationId, pageable);
        var userIds =
                page.getContent().stream()
                        .map(AuditLog::getUtilisateurId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<UUID, String> emails = new HashMap<>();
        if (!userIds.isEmpty()) {
            utilisateurRepository.findAllById(userIds).forEach(x -> emails.put(x.getId(), x.getEmail()));
        }
        return page.map(
                log ->
                        new AuditLogResponse(
                                log.getId(),
                                log.getDateAction(),
                                log.getAction(),
                                log.getEntite(),
                                log.getEntiteId(),
                                log.getAvant(),
                                log.getApres(),
                                log.getUtilisateurId(),
                                log.getUtilisateurId() == null
                                        ? null
                                        : emails.getOrDefault(log.getUtilisateurId(), "?"),
                                log.getIpAddress(),
                                log.getUserAgent()));
    }

    /** Interdit de retirer le dernier ADMIN actif de l'organisation. */
    private void guardLastAdmin(Utilisateur u, AdminUserUpdateRequest req) {
        Role newRole = req.role() != null ? req.role() : u.getRole();
        Boolean newActif = req.actif() != null ? req.actif() : u.isActif();
        boolean losingActiveAdmin =
                u.getRole() == Role.ADMIN
                        && u.isActif()
                        && (newRole != Role.ADMIN || Boolean.FALSE.equals(newActif));
        if (!losingActiveAdmin) {
            return;
        }
        long activeAdmins =
                utilisateurRepository.countByOrganisationIdAndRoleAndActifTrue(
                        u.getOrganisationId(), Role.ADMIN);
        if (activeAdmins <= 1) {
            throw BusinessException.badRequest("LAST_ADMIN_FORBIDDEN");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Map<String, Object> snapshotUser(Utilisateur u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", u.getEmail());
        m.put("nom", u.getNom());
        m.put("prenom", u.getPrenom());
        m.put("role", u.getRole() != null ? u.getRole().name() : null);
        m.put("actif", u.isActif());
        return m;
    }

    private AdminUserResponse toUserResponse(Utilisateur u) {
        return new AdminUserResponse(
                u.getId(),
                u.getEmail(),
                u.getNom(),
                u.getPrenom(),
                u.getRole().name(),
                u.isActif(),
                u.getCreatedAt());
    }
}
