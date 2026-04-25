package com.app.modules.notifications.service;

import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.finance.entity.Facture;
import com.app.modules.finance.entity.StatutFacture;
import com.app.modules.finance.repository.FactureRepository;
import com.app.modules.notifications.entity.NotificationType;
import com.app.modules.rh.entity.HistoriqueSalaire;
import com.app.modules.rh.entity.PaiementSalaire;
import com.app.modules.rh.entity.StatutPaie;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.PaiementSalaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final FactureRepository factureRepository;
    private final PaiementSalaireRepository paiementSalaireRepository;
    private final HistoriqueSalaireRepository historiqueSalaireRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * MON-FRI")
    @Transactional
    public void checkFacturesEnRetard() {
        LocalDate threshold = LocalDate.now(ZoneId.systemDefault()).minusDays(30);
        List<Facture> factures = factureRepository.findByStatutAndDateFactureBefore(StatutFacture.A_PAYER, threshold);
        if (factures.isEmpty()) return;

        Map<UUID, Integer> countByOrg = new HashMap<>();
        for (Facture f : factures) {
            countByOrg.merge(f.getOrganisationId(), 1, Integer::sum);
        }

        for (var e : countByOrg.entrySet()) {
            UUID orgId = e.getKey();
            int nb = e.getValue();
            notifierRoles(orgId, List.of(Role.FINANCIER), NotificationType.FACTURE_EN_RETARD,
                    "Factures en retard",
                    nb == 1 ? "1 facture est en retard de paiement (> 30 jours)." : (nb + " factures sont en retard de paiement (> 30 jours)."),
                    "/finance/factures");
        }
    }

    @Scheduled(cron = "0 0 8 1 * *")
    @Transactional
    public void checkSalairesEnAttente() {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        int mois = now.getMonthValue();
        int annee = now.getYear();

        List<PaiementSalaire> list = paiementSalaireRepository.findByStatutAndAnneeAndMois(StatutPaie.EN_ATTENTE, annee, mois);
        if (list.isEmpty()) return;

        Map<UUID, Integer> countByOrg = new HashMap<>();
        for (PaiementSalaire p : list) {
            countByOrg.merge(p.getOrganisationId(), 1, Integer::sum);
        }

        for (var e : countByOrg.entrySet()) {
            UUID orgId = e.getKey();
            int nb = e.getValue();
            notifierRoles(orgId, List.of(Role.FINANCIER), NotificationType.SALAIRE_DU,
                    "Salaires en attente",
                    nb == 1 ? "1 paiement de salaire est en attente ce mois-ci." : (nb + " paiements de salaires sont en attente ce mois-ci."),
                    "/rh/paie");
        }
    }

    @Scheduled(cron = "0 0 8 * * MON")
    @Transactional
    public void checkContratsExpirants() {
        LocalDate from = LocalDate.now(ZoneId.systemDefault());
        LocalDate to = from.plusDays(30);
        List<HistoriqueSalaire> ending = historiqueSalaireRepository.findEndingBetween(from, to);
        if (ending.isEmpty()) return;

        Map<UUID, Integer> countByOrg = new HashMap<>();
        for (HistoriqueSalaire h : ending) {
            Salarie s = h.getSalarie();
            if (s == null) continue;
            countByOrg.merge(s.getOrganisationId(), 1, Integer::sum);
        }

        for (var e : countByOrg.entrySet()) {
            UUID orgId = e.getKey();
            int nb = e.getValue();
            notifierRoles(orgId, List.of(Role.RH), NotificationType.CONTRAT_EXPIRE_BIENTOT,
                    "Contrats expirants",
                    nb == 1 ? "1 contrat arrive à échéance dans les 30 prochains jours." : (nb + " contrats arrivent à échéance dans les 30 prochains jours."),
                    "/rh/salaries");
        }
    }

    private void notifierRoles(UUID orgId, List<Role> roles, NotificationType type, String titre, String message, String lien) {
        List<Utilisateur> users = utilisateurRepository.findByOrganisationIdAndRoleInAndActifTrue(orgId, roles);
        for (Utilisateur u : users) {
            notificationService.envoyer(orgId, u.getId(), type, titre, message, lien);
        }
    }
}

