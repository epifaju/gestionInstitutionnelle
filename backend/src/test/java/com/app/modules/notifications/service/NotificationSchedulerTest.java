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
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutPaie;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.PaiementSalaireRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock private FactureRepository factureRepository;
    @Mock private PaiementSalaireRepository paiementSalaireRepository;
    @Mock private HistoriqueSalaireRepository historiqueSalaireRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private NotificationScheduler scheduler;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Test
    void checkFacturesEnRetard_sansFactures_neNotifiePas() {
        when(factureRepository.findByStatutAndDateFactureBefore(eq(StatutFacture.A_PAYER), any(LocalDate.class)))
                .thenReturn(List.of());

        scheduler.checkFacturesEnRetard();

        verify(notificationService, never()).envoyer(any(), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void checkFacturesEnRetard_notifieFinanciers() {
        Facture f = new Facture();
        f.setOrganisationId(orgId);
        when(factureRepository.findByStatutAndDateFactureBefore(eq(StatutFacture.A_PAYER), any(LocalDate.class)))
                .thenReturn(List.of(f));

        Utilisateur fin = new Utilisateur();
        fin.setId(userId);
        when(utilisateurRepository.findByOrganisationIdAndRoleInAndActifTrue(eq(orgId), anyList()))
                .thenReturn(List.of(fin));

        scheduler.checkFacturesEnRetard();

        verify(notificationService)
                .envoyer(
                        eq(orgId),
                        eq(userId),
                        eq(NotificationType.FACTURE_EN_RETARD),
                        eq("Factures en retard"),
                        anyString(),
                        eq("/finance/factures"));
    }

    @Test
    void checkSalairesEnAttente_notifieFinanciers() {
        PaiementSalaire p = new PaiementSalaire();
        p.setOrganisationId(orgId);
        when(paiementSalaireRepository.findByStatutAndAnneeAndMois(eq(StatutPaie.EN_ATTENTE), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of(p, p));

        Utilisateur fin = new Utilisateur();
        fin.setId(userId);
        when(utilisateurRepository.findByOrganisationIdAndRoleInAndActifTrue(eq(orgId), anyList()))
                .thenReturn(List.of(fin));

        scheduler.checkSalairesEnAttente();

        verify(notificationService)
                .envoyer(
                        eq(orgId),
                        eq(userId),
                        eq(NotificationType.SALAIRE_DU),
                        eq("Salaires en attente"),
                        anyString(),
                        eq("/rh/paie"));
    }

    @Test
    void checkContratsExpirants_notifieRh() {
        Salarie s = new Salarie();
        s.setOrganisationId(orgId);
        HistoriqueSalaire h = new HistoriqueSalaire();
        h.setSalarie(s);
        when(historiqueSalaireRepository.findEndingBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(h));

        Utilisateur rh = new Utilisateur();
        rh.setId(userId);
        when(utilisateurRepository.findByOrganisationIdAndRoleInAndActifTrue(eq(orgId), eq(List.of(Role.RH))))
                .thenReturn(List.of(rh));

        scheduler.checkContratsExpirants();

        verify(notificationService)
                .envoyer(
                        eq(orgId),
                        eq(userId),
                        eq(NotificationType.CONTRAT_EXPIRE_BIENTOT),
                        eq("Contrats expirants"),
                        anyString(),
                        eq("/rh/salaries"));
    }
}
