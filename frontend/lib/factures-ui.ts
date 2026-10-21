import type { FactureResponse } from "@/lib/types/finance";

export const FACTURE_STATUT_CODES = ["BROUILLON", "A_PAYER", "PAYE", "ANNULE"] as const;
export type FactureStatutCode = (typeof FACTURE_STATUT_CODES)[number];

export function isFactureStatut(v: string): v is FactureStatutCode {
  return (FACTURE_STATUT_CODES as readonly string[]).includes(v);
}

export function statutBadge(s: string): "muted" | "warning" | "success" | "dangerSolid" {
  if (s === "BROUILLON") return "muted";
  if (s === "A_PAYER") return "warning";
  if (s === "PAYE") return "success";
  return "dangerSolid";
}

export function fmtMontant(v: string | number) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  return Number.isNaN(n) ? String(v) : n.toFixed(2);
}

export function buildFacturesCsv(rows: FactureResponse[], headerLine: string) {
  return [
    headerLine,
    ...rows.map((r) =>
      [
        r.reference,
        r.fournisseur,
        r.dateFacture,
        fmtMontant(r.montantHt),
        fmtMontant(r.montantTtc),
        r.categorieLibelle ?? "",
        r.statut,
      ].join(";")
    ),
  ].join("\n");
}
