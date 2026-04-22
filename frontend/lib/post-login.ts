/** Aligné PRD §9 : pas de tableau de bord KPI pour EMPLOYE — accueil sur les congés. */
export const EMPLOYEE_HOME = "/rh/conges";
export const DASHBOARD_HOME = "/dashboard";

export function getDefaultHomePath(role: string | undefined): string {
  if (role === "EMPLOYE") return EMPLOYEE_HOME;
  return DASHBOARD_HOME;
}
