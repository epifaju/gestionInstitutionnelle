package com.app.modules.notifications.service;

import com.app.modules.notifications.dto.NotificationResponse;
import com.app.modules.notifications.entity.Notification;
import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.notifications.repository.NotificationRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void envoyer(
            UUID orgId,
            UUID utilisateurId,
            NotificationType type,
            String titre,
            String message,
            String lien
    ) {
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

