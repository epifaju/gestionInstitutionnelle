package com.app.modules.payroll.service;

import com.app.modules.payroll.entity.EmployeePayrollProfile;
import com.app.modules.payroll.entity.PayrollCotisation;
import com.app.modules.payroll.entity.PayrollLegalConstant;
import com.app.modules.payroll.entity.PayrollRubrique;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Socle paramétrable : calcule un bulletin "snapshot" à partir de rubriques/cotisations configurées.
 * Les règles France strictes sont gérées par les paramètres (taux, assiettes, plafonds, cadres...).
 */
public class PayrollEngine {

    public record Line(
            String section,
            String code,
            String libelle,
            BigDecimal base,
            BigDecimal tauxSal,
            BigDecimal montantSal,
            BigDecimal tauxPat,
            BigDecimal montantPat,
            int ordre
    ) {}

    public record Result(
            BigDecimal brut,
            BigDecimal totalCotSal,
            BigDecimal totalCotPat,
            BigDecimal netImposable,
            BigDecimal pasTaux,
            BigDecimal pasMontant,
            BigDecimal netAPayer,
            List<Line> lines
    ) {}

    public static Result compute(
            LocalDate datePaiement,
            BigDecimal brutBase,
            BigDecimal netBase,
            String devise,
            EmployeePayrollProfile profile,
            List<PayrollRubrique> rubriques,
            List<PayrollCotisation> cotisations,
            Map<String, PayrollLegalConstant> constantsByCode
    ) {
        BigDecimal brut = nz(brutBase);
        BigDecimal net = nz(netBase);

        BigDecimal pmss = constantsByCode.containsKey("PMSS") ? nz(constantsByCode.get("PMSS").getValeur()) : null;

        List<Line> lines = new ArrayList<>();

        // REMUNERATION: base salary (always present)
        lines.add(new Line("REMUNERATION", "BASE", "Salaire de base", brut, null, brut, null, null, 10));

        // Apply configured rubriques (optional)
        for (PayrollRubrique r : rubriques) {
            BigDecimal base = baseAmount(r.getBaseCode(), brut, net, pmss);
            BigDecimal montant = switch (String.valueOf(r.getModeCalcul())) {
                case "FIXED" -> nz(r.getMontantFixe());
                case "PERCENT_BASE" -> pct(base, r.getTauxSalarial());
                default -> BigDecimal.ZERO;
            };
            if (montant.signum() == 0) continue;
            String section = "GAIN".equalsIgnoreCase(r.getType()) ? "REMUNERATION" : "RETENUE".equalsIgnoreCase(r.getType()) ? "COTISATIONS" : "INFO";
            lines.add(new Line(section, r.getCode(), r.getLibelle(), base, r.getTauxSalarial(), montant, r.getTauxPatronal(), null, safeOrder(r.getOrdreAffichage(), 50)));
            if ("GAIN".equalsIgnoreCase(r.getType())) brut = brut.add(montant);
            if ("RETENUE".equalsIgnoreCase(r.getType())) net = net.subtract(montant);
        }

        // COTISATIONS
        BigDecimal totalSal = BigDecimal.ZERO;
        BigDecimal totalPat = BigDecimal.ZERO;
        boolean cadre = profile != null && profile.isCadre();

        for (PayrollCotisation c : cotisations) {
            if (c.isAppliesCadreOnly() && !cadre) continue;
            if (c.isAppliesNonCadreOnly() && cadre) continue;

            BigDecimal base = baseAmount(c.getAssietteBaseCode(), brut, net, pmss);
            base = applyPlafond(base, c.getPlafondCode(), constantsByCode);
            BigDecimal montantSal = pct(base, c.getTauxSalarial());
            BigDecimal montantPat = pct(base, c.getTauxPatronal());

            if (montantSal.signum() == 0 && montantPat.signum() == 0) continue;

            lines.add(new Line(
                    "COTISATIONS",
                    c.getCode(),
                    c.getLibelle(),
                    base,
                    c.getTauxSalarial(),
                    montantSal,
                    c.getTauxPatronal(),
                    montantPat,
                    safeOrder(c.getOrdreAffichage(), 100)
            ));
            totalSal = totalSal.add(montantSal);
            totalPat = totalPat.add(montantPat);
        }

        BigDecimal netImposable = net.subtract(totalSal); // socle : net imposable = net - cotisations salariales (à affiner via paramétrage)

        BigDecimal pasTaux = profile != null ? profile.getTauxPas() : null;
        BigDecimal pas = pct(netImposable.max(BigDecimal.ZERO), pasTaux);

        lines.add(new Line("IMPOT", "PAS", "Prélèvement à la source", netImposable, pasTaux, pas, null, null, 900));

        BigDecimal netAPayer = netImposable.subtract(pas).max(BigDecimal.ZERO);
        lines.add(new Line("NET", "NET_A_PAYER", "Net à payer", null, null, netAPayer, null, null, 1000));

        return new Result(round2(brut), round2(totalSal), round2(totalPat), round2(netImposable), pasTaux, round2(pas), round2(netAPayer), lines);
    }

    private static int safeOrder(Integer v, int def) {
        return v == null ? def : v;
    }

    private static BigDecimal baseAmount(String baseCode, BigDecimal brut, BigDecimal net, BigDecimal pmss) {
        String b = baseCode == null ? "BASE_BRUT" : baseCode;
        return switch (b) {
            case "BASE_NET" -> nz(net);
            case "BASE_NET_IMPOSABLE" -> nz(net); // placeholder: overwritten later
            case "BASE_BRUT_PLAFONNE" -> pmss == null ? nz(brut) : nz(brut).min(pmss);
            case "BASE_BRUT" -> nz(brut);
            default -> nz(brut);
        };
    }

    private static BigDecimal applyPlafond(BigDecimal base, String plafondCode, Map<String, PayrollLegalConstant> constantsByCode) {
        if (base == null) return BigDecimal.ZERO;
        if (plafondCode == null || plafondCode.isBlank()) return base;
        PayrollLegalConstant c = constantsByCode.get(plafondCode.trim());
        if (c == null || c.getValeur() == null) return base;
        return base.min(c.getValeur());
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal taux) {
        if (base == null || taux == null) return BigDecimal.ZERO;
        return base.multiply(taux).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal round2(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }
}

