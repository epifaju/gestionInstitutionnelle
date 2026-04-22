package com.app.modules.rh.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DroitsCongesUtilTest {

    @Test
    void moisTravailles_anneeComplete() {
        assertThat(DroitsCongesUtil.moisTravaillesDansAnneeCivile(LocalDate.of(2020, 1, 1), 2026)).isEqualTo(12);
    }

    @Test
    void moisTravailles_embaucheMilieuDAnnee() {
        assertThat(DroitsCongesUtil.moisTravaillesDansAnneeCivile(LocalDate.of(2026, 7, 1), 2026)).isEqualTo(6);
    }

    @Test
    void joursDroit_12mois_plafond30() {
        assertThat(DroitsCongesUtil.joursDroitTheoriquesPourMois(12)).isEqualByComparingTo(new BigDecimal("30.0"));
    }

    @Test
    void joursDroit_6mois() {
        assertThat(DroitsCongesUtil.joursDroitTheoriquesPourMois(6)).isEqualByComparingTo(new BigDecimal("15.0"));
    }
}
