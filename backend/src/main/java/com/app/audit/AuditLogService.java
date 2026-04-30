package com.app.audit;

import com.app.audit.entity.AuditLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_USER_AGENT_LEN = 2000;
    private static final java.util.Set<String> SENSITIVE_KEYS =
            java.util.Set.of(
                    "password",
                    "passwordHash",
                    "newPassword",
                    "token",
                    "accessToken",
                    "refreshToken",
                    "secret",
                    "secretKey",
                    "apiKey",
                    "authorization");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRh(UUID organisationId, UUID utilisateurId, String action, String entite, UUID entiteId, Object avant, Object apres) {
        log(organisationId, utilisateurId, action, entite, entiteId, avant, apres);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID organisationId, UUID utilisateurId, String action, String entite, UUID entiteId, Object avant, Object apres) {
        AuditLog log = new AuditLog();
        log.setOrganisationId(organisationId);
        log.setUtilisateurId(utilisateurId);
        log.setAction(action);
        log.setEntite(entite);
        log.setEntiteId(entiteId);
        log.setAvant(toJson(avant));
        log.setApres(toJson(apres));
        log.setDateAction(Instant.now());
        enrichFromCurrentRequest(log);
        auditLogRepository.save(log);
    }

    /**
     * Renseigne IP (X-Forwarded-For ou remote) et User-Agent lorsque l’appel provient d’une requête HTTP
     * (même thread que le contrôleur).
     */
    private static void enrichFromCurrentRequest(AuditLog log) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return;
        }
        HttpServletRequest req = sra.getRequest();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            log.setIpAddress(xff.split(",")[0].trim());
        } else {
            log.setIpAddress(req.getRemoteAddr());
        }
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > MAX_USER_AGENT_LEN) {
            ua = ua.substring(0, MAX_USER_AGENT_LEN);
        }
        log.setUserAgent(ua);
    }

    private JsonNode toJson(Object o) {
        if (o == null) {
            return null;
        }
        JsonNode node = objectMapper.valueToTree(o);
        scrubSensitive(node);
        return node;
    }

    /**
     * Best-effort scrubbing for known sensitive keys before persisting audit logs.
     * This reduces blast radius if audit logs are exported or leaked.
     */
    private static void scrubSensitive(JsonNode node) {
        if (node == null) return;
        if (node instanceof ObjectNode obj) {
            var it = obj.fieldNames();
            java.util.List<String> names = new java.util.ArrayList<>();
            it.forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = obj.get(name);
                if (name != null && SENSITIVE_KEYS.contains(name)) {
                    obj.put(name, "[REDACTED]");
                } else {
                    scrubSensitive(child);
                }
            }
        } else if (node instanceof ArrayNode arr) {
            for (JsonNode child : arr) {
                scrubSensitive(child);
            }
        }
    }
}
