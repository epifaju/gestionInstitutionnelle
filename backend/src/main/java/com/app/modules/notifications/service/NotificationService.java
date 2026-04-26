package com.app.modules.notifications.service;

import com.app.modules.notifications.dto.NotificationResponse;
import com.app.modules.notifications.entity.Notification;
import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.notifications.repository.NotificationRepository;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.shared.email.EmailService;
import com.app.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UtilisateurRepository utilisateurRepository;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    @Transactional
    public void envoyer(
            UUID orgId,
            UUID utilisateurId,
            NotificationType type,
            String titre,
            String message,
            String lien
    ) {
        // Préférences utilisateur : si utilisateurId != null, on peut filtrer
        // - UI disabled => on ne crée pas de ligne notification
        // - Email enabled => on envoie un email
        if (utilisateurId != null) {
            Utilisateur u = utilisateurRepository.findById(utilisateurId)
                    .orElse(null);
            if (u != null) {
                Preferences prefs = Preferences.fromJson(objectMapper, u.getPreferences());
                if (!prefs.isUiEnabled(type.name())) {
                    // UI désactivée => pas de notif DB/websocket.
                    // On continue quand même pour l'email si activé.
                    if (prefs.isEmailEnabled(type.name())) {
                        emailService.send(u.getEmail(), titre, buildEmailBody(message, lien));
                    }
                    return;
                }
                if (prefs.isEmailEnabled(type.name())) {
                    emailService.send(u.getEmail(), titre, buildEmailBody(message, lien));
                }
            }
        }

        Notification n = new Notification();
        n.setOrganisationId(orgId);
        n.setUtilisateurId(utilisateurId);
        n.setType(type);
        n.setTitre(titre);
        n.setMessage(message);
        n.setLien(lien);
        n.setLu(false);
        notificationRepository.save(n);

        NotificationResponse payload = toResponse(n);
        if (utilisateurId != null) {
            messagingTemplate.convertAndSend("/queue/notifications/" + utilisateurId, payload);
        } else {
            messagingTemplate.convertAndSend("/topic/org/" + orgId + "/notifications", payload);
        }
    }

    private static String buildEmailBody(String message, String lien) {
        if (lien == null || lien.isBlank()) {
            return message;
        }
        return message + "\n\n" + lien.trim();
    }

    private record Preferences(HashSet<String> uiEnabled, HashSet<String> emailEnabled) {
        static Preferences fromJson(ObjectMapper om, String json) {
            try {
                if (json == null || json.isBlank()) return new Preferences(new HashSet<>(), new HashSet<>());
                JsonNode root = om.readTree(json);
                HashSet<String> ui = readSet(root.path("notifications").path("uiEnabled"));
                HashSet<String> email = readSet(root.path("notifications").path("emailEnabled"));
                return new Preferences(ui, email);
            } catch (Exception ex) {
                return new Preferences(new HashSet<>(), new HashSet<>());
            }
        }

        boolean isUiEnabled(String type) {
            // par défaut: tout activé si liste vide
            return uiEnabled == null || uiEnabled.isEmpty() || uiEnabled.contains(type);
        }

        boolean isEmailEnabled(String type) {
            // par défaut: pas d'emails si liste vide
            return emailEnabled != null && emailEnabled.contains(type);
        }

        private static HashSet<String> readSet(JsonNode n) {
            HashSet<String> set = new HashSet<>();
            if (n == null || !n.isArray()) return set;
            for (JsonNode it : n) {
                if (it != null && it.isTextual()) {
                    String v = it.asText().trim();
                    if (!v.isEmpty()) set.add(v);
                }
            }
            return set;
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMesNotifications(UUID userId, boolean nonLuesSeulement, Pageable p) {
        Page<Notification> page = nonLuesSeulement
                ? notificationRepository.findByUtilisateurIdAndLuFalseOrderByCreatedAtDesc(userId, p)
                : notificationRepository.findByUtilisateurIdOrderByCreatedAtDesc(userId, p);
        return page.map(this::toResponse);
    }

    @Transactional
    public void marquerLu(UUID notifId, UUID userId) {
        int updated = notificationRepository.markRead(notifId, userId);
        if (updated == 0) throw BusinessException.notFound("NOTIFICATION_NOT_FOUND");
    }

    @Transactional
    public void marquerToutLu(UUID userId) {
        notificationRepository.markAllRead(userId);
    }

    @Transactional(readOnly = true)
    public long compterNonLues(UUID userId) {
        return notificationRepository.countByUtilisateurIdAndLuFalse(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        LocalDateTime created = n.getCreatedAt() != null
                ? LocalDateTime.ofInstant(n.getCreatedAt(), ZoneOffset.UTC)
                : null;
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitre(),
                n.getMessage(),
                n.getLien(),
                n.isLu(),
                created
        );
    }
}

