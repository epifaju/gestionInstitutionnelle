import { describe, expect, it } from "vitest";
import { DASHBOARD_HOME, EMPLOYEE_HOME, getDefaultHomePath } from "./post-login";

describe("getDefaultHomePath", () => {
  it("redirige EMPLOYE vers les congés", () => {
    expect(getDefaultHomePath("EMPLOYE")).toBe(EMPLOYEE_HOME);
  });

  it("redirige les autres rôles vers le dashboard", () => {
    expect(getDefaultHomePath("ADMIN")).toBe(DASHBOARD_HOME);
    expect(getDefaultHomePath("RH")).toBe(DASHBOARD_HOME);
    expect(getDefaultHomePath("FINANCIER")).toBe(DASHBOARD_HOME);
  });

  it("traite role undefined comme dashboard", () => {
    expect(getDefaultHomePath(undefined)).toBe(DASHBOARD_HOME);
  });
});
