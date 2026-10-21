const STATUT_CODES = ["EN_ATTENTE", "VALIDE", "REJETE", "ANNULE", "BROUILLON"] as const;
const TYPE_CODES = ["ANNUEL", "MALADIE", "EXCEPTIONNEL", "SANS_SOLDE"] as const;

export type CongeStatutCode = (typeof STATUT_CODES)[number];
export type CongeTypeCode = (typeof TYPE_CODES)[number];

export function isCongeStatutCode(v: string): v is CongeStatutCode {
  return (STATUT_CODES as readonly string[]).includes(v);
}

export function isCongeTypeCode(v: string): v is CongeTypeCode {
  return (TYPE_CODES as readonly string[]).includes(v);
}

export function startOfMonth(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

export function endOfMonth(d: Date) {
  return new Date(d.getFullYear(), d.getMonth() + 1, 0);
}

export function fmtIso(d: Date) {
  return d.toISOString().slice(0, 10);
}

/** Congé validé annulable seulement si la date de début est strictement après aujourd'hui. */
export function canAnnulerCongeValide(dateDebutIso: string, today = new Date()) {
  const start = new Date(dateDebutIso + "T12:00:00");
  const t = new Date(today);
  t.setHours(0, 0, 0, 0);
  start.setHours(0, 0, 0, 0);
  return start.getTime() > t.getTime();
}

export { STATUT_CODES as CONGE_STATUT_CODES, TYPE_CODES as CONGE_TYPE_CODES };
