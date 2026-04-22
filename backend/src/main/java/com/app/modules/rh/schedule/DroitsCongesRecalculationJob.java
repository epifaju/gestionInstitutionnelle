package com.app.modules.rh.schedule;

import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rh.entity.DroitsConges;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutConge;
import com.app.modules.rh.entity.StatutSalarie;
import com.app.modules.rh.entity.TypeConge;
import com.app.modules.rh.repository.CongeRepository;
import com.app.modules.rh.repository.DroitsCongesRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.util.DroitsCongesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recalcul annuel des droits (PRD §8.1) : 2,5 j/mois, plafond 30 j, cohérence avec les congés validés.
 * Exécuté le 1er janvier après la ligne de paie planifiée ({@link com.app.modules.rh.service.PaieService}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DroitsCongesRecalculationJob {

    private final OrganisationRepository organisationRepository;
    private final SalarieRepository salarieRepository;
    private final DroitsCongesRepository droitsCongesRepository;
    private final CongeRepository congeRepository;

    @Scheduled(cron = "0 15 1 1 * *")
    @Transactional
    public void recalculerDroitsNouvelAn() {
        int annee = LocalDate.now().getYear();
        LocalDate debutAnnee = LocalDate.of(annee, 1, 1);
        LocalDate finAnnee = LocalDate.of(annee, 12, 31);

        organisationRepository
                .findByActifTrue()
                .forEach(
                        org -> {
                            UUID orgId = org.getId();
                            List<Salarie> salaries =
                                    salarieRepository.findByOrganisationIdAndStatut(orgId, StatutSalarie.ACTIF);
                            for (Salarie s : salaries) {
                                try {
                                    int mois =
                                            DroitsCongesUtil.moisTravaillesDansAnneeCivile(
                                                    s.getDateEmbauche(), annee);
                                    BigDecimal joursDroit = DroitsCongesUtil.joursDroitTheoriquesPourMois(mois);

                                    BigDecimal joursPris =
                                            congeRepository
                                                    .sumJoursValidesSoldeEntreDates(
                                                            s.getId(),
                                                            StatutConge.VALIDE,
                                                            TypeConge.ANNUEL,
                                                            TypeConge.EXCEPTIONNEL,
                                                            debutAnnee,
                                                            finAnnee)
                                                    .setScale(1, RoundingMode.HALF_UP);

                                    BigDecimal restants = joursDroit.subtract(joursPris);
                                    if (restants.compareTo(BigDecimal.ZERO) < 0) {
                                        log.warn(
                                                "Solde restant négatif après recalcul (salarie={}, annee={}) — ramené à 0",
                                                s.getId(),
                                                annee);
                                        restants = BigDecimal.ZERO;
                                    }
                                    restants = restants.setScale(1, RoundingMode.HALF_UP);

                                    DroitsConges d =
                                            droitsCongesRepository
                                                    .findBySalarie_IdAndAnnee(s.getId(), annee)
                                                    .orElseGet(
                                                            () -> {
                                                                DroitsConges x = new DroitsConges();
                                                                x.setOrganisationId(orgId);
                                                                x.setSalarie(s);
                                                                x.setAnnee(annee);
                                                                return x;
                                                            });
                                    d.setJoursDroit(joursDroit);
                                    d.setJoursPris(joursPris);
                                    d.setJoursRestants(restants);
                                    droitsCongesRepository.save(d);
                                } catch (Exception e) {
                                    log.error(
                                            "Recalcul droits congés échoué (salarie={}, annee={})",
                                            s.getId(),
                                            annee,
                                            e);
                                }
                            }
                        });

        log.info("Recalcul droits congés terminé pour l'année {}", annee);
    }
}
