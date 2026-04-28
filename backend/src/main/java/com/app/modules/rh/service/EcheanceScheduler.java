package com.app.modules.rh.service;

import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.notifications.service.NotificationService;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rh.entity.ContratSalarie;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.FormationObligatoire;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.TypeEcheance;
import com.app.modules.rh.repository.ContratSalarieRepository;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.FormationObligatoireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EcheanceScheduler {

    private final EcheanceRhRepository echeanceRepository;
    private final FormationObligatoireRepository formationRepository;
    private final ContratSalarieRepository contratRepository;
    private final OrganisationRepository organisationRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 7 * * MON-FRI")
    @Transactional
    public void verifierEcheancesQuotidien() {
        LocalDate today = LocalDate.now();

        // Étape 1 — Rappels J-30
        for (EcheanceRh e : echeanceRepository.findEcheancesAlerteJ30(today)) {
            NotificationType nt = notifType(e.getTypeEcheance());
            String msg = "Échéance dans 30 jours : " + e.getTitre() + " (" + (e.getSalarie().getNom() + " " + e.getSalarie().getPrenom()).trim() + ")";
            notificationService.envoyer(e.getOrganisationId(), null, nt, "Échéance RH", msg, "/rh/contrats/echeances/" + e.getId());
            if (e.getStatut() == StatutEcheance.A_VENIR) e.setStatut(StatutEcheance.EN_ALERTE);
            e.setRappelJ30Envoye(true);
            echeanceRepository.save(e);
        }

        // Étape 2 — Rappels J-7
        for (EcheanceRh e : echeanceRepository.findEcheancesAlerteJ7(today)) {
            NotificationType nt = notifType(e.getTypeEcheance());
            String msg = "Échéance dans 7 jours : " + e.getTitre() + " (" + (e.getSalarie().getNom() + " " + e.getSalarie().getPrenom()).trim() + ")";
            notificationService.envoyer(e.getOrganisationId(), null, nt, "Échéance RH (urgent)", msg, "/rh/contrats/echeances/" + e.getId());
            e.setStatut(StatutEcheance.ACTION_REQUISE);
            e.setRappelJ7Envoye(true);
            echeanceRepository.save(e);
        }

        // Étape 3 — Marquer EXPIREE
        for (EcheanceRh e : echeanceRepository.findEcheancesExpirees(today)) {
            e.setStatut(StatutEcheance.EXPIREE);
            if (!e.isRappelJ0Envoye()) {
                NotificationType nt = notifType(e.getTypeEcheance());
                String msg = "Échéance dépassée : " + e.getTitre() + " (" + (e.getSalarie().getNom() + " " + e.getSalarie().getPrenom()).trim() + ")";
                notificationService.envoyer(e.getOrganisationId(), null, nt, "Échéance RH (dépassée)", msg, "/rh/contrats/echeances/" + e.getId());
                e.setRappelJ0Envoye(true);
            }
            echeanceRepository.save(e);
        }

        // Étape 4 — Formations expirées
        for (FormationObligatoire f : formationRepository.findToutesExpireesAvant(today)) {
            f.setStatut("EXPIREE");
            formationRepository.save(f);

            // Créer une échéance d'urgence si elle n'existe pas déjà
            var s = f.getSalarie();
            if (s == null) continue;
            boolean exists = echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                    f.getOrganisationId(),
                    s.getId(),
                    TypeEcheance.FORMATION_OBLIGATOIRE,
                    today,
                    List.of(StatutEcheance.TRAITEE, StatutEcheance.ANNULEE, StatutEcheance.EXPIREE)
            );
            if (!exists) {
                EcheanceRh e = new EcheanceRh();
                e.setOrganisationId(f.getOrganisationId());
                e.setSalarie(s);
                e.setTypeEcheance(TypeEcheance.FORMATION_OBLIGATOIRE);
                e.setTitre("Formation expirée — action requise");
                e.setDescription("La formation/certification est expirée. Veuillez planifier un renouvellement.");
                e.setDateEcheance(today);
                e.setStatut(StatutEcheance.ACTION_REQUISE);
                e.setPriorite(1);
                echeanceRepository.save(e);
            }
        }
    }

    @Scheduled(cron = "0 0 8 1 * *")
    @Transactional
    public void verifierCddProchainsMois() {
        LocalDate today = LocalDate.now();
        LocalDate max = today.plusDays(90);
        for (var org : organisationRepository.findByActifTrue()) {
            List<ContratSalarie> cdds = contratRepository.findCddExpirantDans(org.getId(), today, max);
            for (ContratSalarie c : cdds) {
                if (c.getDecisionFin() != null && c.getDecisionFin().name().equals("EN_ATTENTE")) {
                    String msg = "Décision requise pour CDD de " + (c.getSalarie().getNom() + " " + c.getSalarie().getPrenom()).trim();
                    notificationService.envoyer(org.getId(), null, NotificationType.ECHEANCE_FIN_CDD, "Décision CDD", msg, "/rh/contrats");
                }
            }
        }
    }

    private static NotificationType notifType(TypeEcheance t) {
        if (t == null) return NotificationType.ECHEANCE_AUTRE;
        return switch (t) {
            case FIN_CDD -> NotificationType.ECHEANCE_FIN_CDD;
            case FIN_PERIODE_ESSAI -> NotificationType.ECHEANCE_FIN_PERIODE_ESSAI;
            case VISITE_MEDICALE -> NotificationType.ECHEANCE_VISITE_MEDICALE;
            case TITRE_SEJOUR -> NotificationType.ECHEANCE_TITRE_SEJOUR;
            case FORMATION_OBLIGATOIRE -> NotificationType.ECHEANCE_FORMATION_OBLIGATOIRE;
            case AVENANT_CONTRAT -> NotificationType.ECHEANCE_AVENANT_CONTRAT;
            case RENOUVELLEMENT_CDD, AUTRE -> NotificationType.ECHEANCE_AUTRE;
        };
    }
}

