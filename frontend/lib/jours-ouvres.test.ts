import { describe, expect, it } from "vitest";
import { compterJoursOuvres } from "./jours-ouvres";

describe("compterJoursOuvres", () => {
  it("compte une semaine complète (lun–ven = 5)", () => {
    // 2026-04-06 = lundi, 2026-04-10 = vendredi
    expect(compterJoursOuvres(new Date(2026, 3, 6), new Date(2026, 3, 10))).toBe(5);
  });

  it("ignore samedi et dimanche", () => {
    // ven 10 → lun 13 = ven + lun = 2
    expect(compterJoursOuvres(new Date(2026, 3, 10), new Date(2026, 3, 13))).toBe(2);
  });

  it("retourne 0 si fin < début", () => {
    expect(compterJoursOuvres(new Date(2026, 3, 10), new Date(2026, 3, 6))).toBe(0);
  });

  it("compte 1 pour un seul jour ouvré", () => {
    expect(compterJoursOuvres(new Date(2026, 3, 8), new Date(2026, 3, 8))).toBe(1);
  });

  it("retourne 0 pour un dimanche seul", () => {
    expect(compterJoursOuvres(new Date(2026, 3, 12), new Date(2026, 3, 12))).toBe(0);
  });
});
