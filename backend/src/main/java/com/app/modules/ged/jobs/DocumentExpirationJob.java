package com.app.modules.ged.jobs;

import com.app.modules.auth.entity.Role;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.ged.service.DocumentService;
import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentExpirationJob {

    private final OrganisationRepository organisationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentService documentService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * MON")
    public void notifyExpiringDocs() {
        var orgs = organisationRepository.findAll();
        for (var org : orgs) {
            var list = documentService.getDocumentsExpirantBientot(org.getId(), 30);
            if (list.isEmpty()) continue;

            String titre = "Documents expirant bientôt";
            String msg = "Vous avez " + list.size() + " document(s) expirant dans les 30 prochains jours.";
            String lien = "/documents?expirantBientot=true";

            var recipients =
                    utilisateurRepository.findByOrganisationIdAndRoleInAndActifTrue(
                            org.getId(), List.of(Role.RH, Role.ADMIN));

            for (var u : recipients) {
                notificationService.envoyer(org.getId(), u.getId(), NotificationType.DOCUMENT_EXPIRE_BIENTOT, titre, msg, lien);
            }
        }
    }
}

