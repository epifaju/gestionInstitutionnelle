package com.app.modules.notifications.service;

import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.notifications.entity.Notification;
import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.notifications.repository.NotificationRepository;
import com.app.shared.email.EmailService;
import com.app.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock UtilisateurRepository utilisateurRepository;
    @Mock ObjectMapper objectMapper;
    @Mock EmailService emailService;

    @InjectMocks NotificationService service;

    @Test
    void envoyer_sendsToUserQueue_whenUserIdProvided() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // no preferences -> default: UI enabled
        Utilisateur u = new Utilisateur();
        u.setEmail("user@test.local");
        u.setPreferences("{}");
        when(utilisateurRepository.findById(userId)).thenReturn(java.util.Optional.of(u));

        service.envoyer(orgId, userId, NotificationType.SALAIRE_DU, "Titre", "Msg", "/lien");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(orgId);
        assertThat(saved.getUtilisateurId()).isEqualTo(userId);
        assertThat(saved.isLu()).isFalse();

        verify(messagingTemplate).convertAndSend(eq("/queue/notifications/" + userId), any(Object.class));
    }

    @Test
    void envoyer_sendsToOrgTopic_whenUserIdMissing() {
        UUID orgId = UUID.randomUUID();
        service.envoyer(orgId, null, NotificationType.SALAIRE_DU, "Titre", "Msg", "/lien");
        verify(messagingTemplate).convertAndSend(eq("/topic/org/" + orgId + "/notifications"), any(Object.class));
    }

    @Test
    void marquerLu_throwsNotFound_whenNothingUpdated() {
        UUID notifId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationRepository.markRead(notifId, userId)).thenReturn(0);

        assertThatThrownBy(() -> service.marquerLu(notifId, userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void marquerToutLu_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        service.marquerToutLu(userId);
        verify(notificationRepository).markAllRead(userId);
    }

    @Test
    void compterNonLues_returnsRepositoryCount() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countByUtilisateurIdAndLuFalse(userId)).thenReturn(7L);
        assertThat(service.compterNonLues(userId)).isEqualTo(7L);
    }
}

