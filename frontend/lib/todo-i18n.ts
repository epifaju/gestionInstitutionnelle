import type { useTranslations } from "next-intl";

type TodoT = ReturnType<typeof useTranslations<"Todo">>;

const ROLE_KEYS = ["Ressources Humaines", "Finance", "Administration", "Logistique"] as const;
const CATEGORY_KEYS = [
  "CONGES_A_VALIDER",
  "MISSIONS_A_APPROUVER",
  "FRAIS_A_VALIDER",
  "ECHEANCES_RH_URGENTES",
  "FACTURES_A_PAYER",
  "SALAIRES_A_VERSER",
  "FRAIS_A_REMBOURSER",
  "BUDGET_A_VALIDER",
  "BUDGETS_A_VALIDER",
  "FACTURES_RETARD_CRITIQUE",
  "DOCS_EXPIRANTS",
  "BIENS_DEFAILLANTS",
] as const;

export function translateTodoRole(t: TodoT, roleLabel: string) {
  return (ROLE_KEYS as readonly string[]).includes(roleLabel)
    ? t(`roles.${roleLabel as (typeof ROLE_KEYS)[number]}`)
    : roleLabel;
}

export function translateTodoCategory(t: TodoT, categorie: string, fallback: string) {
  return (CATEGORY_KEYS as readonly string[]).includes(categorie)
    ? t(`categories.${categorie as (typeof CATEGORY_KEYS)[number]}`)
    : fallback;
}

export function formatTodoRelative(t: TodoT, iso: string, opts?: { withJustNow?: boolean }) {
  const d = iso ? new Date(iso) : null;
  if (!d || Number.isNaN(d.getTime())) return "";
  const ms = Date.now() - d.getTime();
  const m = Math.floor(ms / 60_000);
  if (opts?.withJustNow !== false && m < 1) return t("relative.justNow");
  if (m < 60) return t("relative.minutes", { n: Math.max(0, m) });
  const h = Math.floor(m / 60);
  if (h < 24) return t("relative.hours", { n: h });
  const days = Math.floor(h / 24);
  if (days < 14) return t("relative.days", { n: days });
  return t("relative.weeks", { n: Math.floor(days / 7) });
}
