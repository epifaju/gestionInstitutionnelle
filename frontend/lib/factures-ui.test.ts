import { describe, expect, it } from "vitest";
import { buildFacturesCsv, fmtMontant, isFactureStatut, statutBadge } from "./factures-ui";
import type { FactureResponse } from "./types/finance";

describe("factures-ui", () => {
  it("isFactureStatut", () => {
    expect(isFactureStatut("A_PAYER")).toBe(true);
    expect(isFactureStatut("DRAFT")).toBe(false);
  });

  it("statutBadge", () => {
    expect(statutBadge("BROUILLON")).toBe("muted");
    expect(statutBadge("A_PAYER")).toBe("warning");
    expect(statutBadge("PAYE")).toBe("success");
    expect(statutBadge("ANNULE")).toBe("dangerSolid");
  });

  it("fmtMontant", () => {
    expect(fmtMontant(12.5)).toBe("12.50");
    expect(fmtMontant("3")).toBe("3.00");
    expect(fmtMontant("x")).toBe("x");
  });

  it("buildFacturesCsv", () => {
    const row = {
      id: "1",
      reference: "F-1",
      fournisseur: "ACME",
      dateFacture: "2026-04-01",
      montantHt: 100,
      montantTtc: 120,
      devise: "EUR",
      categorieLibelle: "Fournitures",
      statut: "A_PAYER",
    } as FactureResponse;
    const csv = buildFacturesCsv([row], "ref;fourn;date;ht;ttc;cat;statut");
    expect(csv).toContain("F-1;ACME;2026-04-01;100.00;120.00;Fournitures;A_PAYER");
  });
});
