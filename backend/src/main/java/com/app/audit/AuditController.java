package com.app.audit;

import com.app.audit.dto.AuditLogAdminResponse;
import com.app.audit.entity.AuditLog;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogAdminResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) String entite,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID utilisateurId
    ) {
        Periode periode = parsePeriode(dateDebut, dateFin);
        var p =
                auditLogRepository.findForExport(
                        user.getOrganisationId(),
                        periode.start(),
                        periode.end(),
                        blankToNull(entite),
                        blankToNull(action),
                        utilisateurId,
                        PageRequest.of(page, size));

        Map<UUID, com.app.modules.auth.entity.Utilisateur> users = loadUsers(p.getContent());
        PageResponse<AuditLogAdminResponse> out = PageResponse.from(p, a -> toResponse(a, users.get(a.getUtilisateurId())));
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) String entite,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID utilisateurId
    ) {
        Periode periode = parsePeriode(dateDebut, dateFin);
        long c =
                auditLogRepository.countForExport(
                        user.getOrganisationId(),
                        periode.start(),
                        periode.end(),
                        blankToNull(entite),
                        blankToNull(action),
                        utilisateurId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", c)));
    }

    private record Periode(Instant start, Instant end) {}

    private static Periode parsePeriode(String dateDebut, String dateFin) {
        try {
            LocalDate d1 = LocalDate.parse(dateDebut);
            LocalDate d2 = LocalDate.parse(dateFin);
            if (d1.isAfter(d2)) throw BusinessException.badRequest("EXPORT_PERIODE_INVALIDE");
            Instant start = d1.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = d2.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            return new Periode(start, end);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.badRequest("EXPORT_PERIODE_INVALIDE");
        }
    }

    private Map<UUID, com.app.modules.auth.entity.Utilisateur> loadUsers(List<AuditLog> logs) {
        Map<UUID, com.app.modules.auth.entity.Utilisateur> out = new HashMap<>();
        List<UUID> ids =
                logs.stream()
                        .map(AuditLog::getUtilisateurId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) return out;
        utilisateurRepository.findAllById(ids).forEach(u -> out.put(u.getId(), u));
        return out;
    }

    private static AuditLogAdminResponse toResponse(AuditLog a, com.app.modules.auth.entity.Utilisateur u) {
        String email = u != null ? u.getEmail() : null;
        String role = u != null && u.getRole() != null ? u.getRole().name() : null;
        LocalDateTime dt =
                a.getDateAction() == null ? null : LocalDateTime.ofInstant(a.getDateAction(), ZoneId.systemDefault());
        return new AuditLogAdminResponse(
                a.getId(),
                email,
                role,
                a.getAction(),
                a.getEntite(),
                a.getEntiteId(),
                a.getAvant(),
                a.getApres(),
                a.getIpAddress(),
                dt);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isBlank() ? null : v;
    }
}

