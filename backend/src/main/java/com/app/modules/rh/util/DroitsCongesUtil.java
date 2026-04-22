package com.app.modules.rh.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Règle PRD §8.1 : 2,5 jours par mois travaillé, plafonné à 30 j/an (12 × 2,5).
 */
public final class DroitsCongesUtil {

    private static final BigDecimal JOURS_PAR_MOIS = new BigDecimal("2.5");
    private static final BigDecimal PLAFOND_ANNUEL = new BigDecimal("30");

    private DroitsCongesUtil() {}

    /**
     * Nombre de mois civils inclus dans l'année {@code annee} à partir de l'embauche
     * (du 1er mois d'activité dans l'année jusqu'à décembre inclus).
     */
    public static int moisTravaillesDansAnneeCivile(LocalDate dateEmbauche, int annee) {
        LocalDate finAnnee = LocalDate.of(annee, 12, 31);
        if (dateEmbauche.isAfter(finAnnee)) {
            return 0;
        }
        LocalDate debutAnnee = LocalDate.of(annee, 1, 1);
        LocalDate debut = dateEmbauche.isAfter(debutAnnee) ? dateEmbauche : debutAnnee;
        YearMonth start = YearMonth.from(debut);
        YearMonth end = YearMonth.of(annee, 12);
        if (start.isAfter(end)) {
            return 0;
        }
        return (end.getYear() * 12 + end.getMonthValue()) - (start.getYear() * 12 + start.getMonthValue()) + 1;
    }

    public static BigDecimal joursDroitTheoriquesPourMois(int moisTravailles) {
        if (moisTravailles <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal d = JOURS_PAR_MOIS.multiply(BigDecimal.valueOf(moisTravailles));
        if (d.compareTo(PLAFOND_ANNUEL) > 0) {
            d = PLAFOND_ANNUEL;
        }
        return d.setScale(1, RoundingMode.HALF_UP);
    }
}
