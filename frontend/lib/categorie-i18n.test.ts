import { describe, expect, it } from "vitest";
import { slugCategorieLibelle, translateCategorieLabel } from "./categorie-i18n";

function mockT(dict: Record<string, string>) {
  const t = ((key: string) => dict[key] ?? key) as ((key: string) => string) & {
    has: (key: string) => boolean;
  };
  t.has = (key: string) => Object.prototype.hasOwnProperty.call(dict, key);
  return t;
}

describe("categorie-i18n", () => {
  it("slugifies French labels", () => {
    expect(slugCategorieLibelle("Frais de service")).toBe("frais_de_service");
    expect(slugCategorieLibelle("Adhésion")).toBe("adhesion");
    expect(slugCategorieLibelle("Équipement")).toBe("equipement");
  });

  it("prefers byCode over byLibelle", () => {
    const t = mockT({
      "byCode.FRSRV": "Taxas de serviço",
      "byLibelle.frais_de_service": "ignored",
    });
    expect(translateCategorieLabel(t, { code: "FRSRV", libelle: "Frais de service" })).toBe(
      "Taxas de serviço"
    );
  });

  it("falls back to byLibelle then raw libelle", () => {
    const t = mockT({ "byLibelle.adhesion": "Adesões" });
    expect(translateCategorieLabel(t, { code: "X", libelle: "Adhésion" })).toBe("Adesões");
    expect(translateCategorieLabel(t, { libelle: "Custom" })).toBe("Custom");
  });
});
