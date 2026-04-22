package com.app.modules.rh.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;

public final class JoursOuvres {

    private JoursOuvres() {}

    public static BigDecimal compter(LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) {
            throw new IllegalArgumentException("dateFin avant dateDebut");
        }
        int n = 0;
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            DayOfWeek dw = d.getDayOfWeek();
            if (dw != DayOfWeek.SATURDAY && dw != DayOfWeek.SUNDAY) {
                n++;
            }
        }
        return new BigDecimal(n).setScale(1, RoundingMode.HALF_UP);
    }
}
