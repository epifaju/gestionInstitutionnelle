import { describe, expect, it } from "vitest";
import { normalizeNotificationLink } from "./notification-link";

describe("normalizeNotificationLink", () => {
  it("retourne null pour vide / null / blank", () => {
    expect(normalizeNotificationLink(null)).toBeNull();
    expect(normalizeNotificationLink(undefined)).toBeNull();
    expect(normalizeNotificationLink("   ")).toBeNull();
  });

  it("réécrit les anciens liens inventaire détail", () => {
    expect(normalizeNotificationLink("/inventaire/biens/abc")).toBe("/inventaire");
  });

  it("laisse passer les liens valides", () => {
    expect(normalizeNotificationLink("/rh/conges")).toBe("/rh/conges");
    expect(normalizeNotificationLink("  /finance/factures  ")).toBe("/finance/factures");
  });
});
