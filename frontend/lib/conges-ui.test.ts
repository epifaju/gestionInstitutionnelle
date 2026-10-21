import { describe, expect, it } from "vitest";
import {
  canAnnulerCongeValide,
  endOfMonth,
  fmtIso,
  isCongeStatutCode,
  isCongeTypeCode,
  startOfMonth,
} from "./conges-ui";

describe("conges-ui", () => {
  it("valide les codes statut / type", () => {
    expect(isCongeStatutCode("VALIDE")).toBe(true);
    expect(isCongeStatutCode("FOO")).toBe(false);
    expect(isCongeTypeCode("ANNUEL")).toBe(true);
    expect(isCongeTypeCode("RTT")).toBe(false);
  });

  it("calcule bornes de mois", () => {
    const d = new Date(2026, 3, 15);
    expect(startOfMonth(d).getDate()).toBe(1);
    expect(endOfMonth(d).getDate()).toBe(30);
  });

  it("fmtIso renvoie YYYY-MM-DD", () => {
    expect(fmtIso(new Date(Date.UTC(2026, 3, 9)))).toBe("2026-04-09");
  });

  it("canAnnulerCongeValide seulement si début futur", () => {
    const today = new Date(2026, 3, 10);
    expect(canAnnulerCongeValide("2026-04-15", today)).toBe(true);
    expect(canAnnulerCongeValide("2026-04-10", today)).toBe(false);
    expect(canAnnulerCongeValide("2026-04-01", today)).toBe(false);
  });
});
