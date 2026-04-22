/** Jours ouvrés entre deux dates (lun–ven), aligné sur la logique backend. */
export function compterJoursOuvres(debut: Date, fin: Date): number {
  if (fin < debut) return 0;
  let n = 0;
  const d = new Date(debut);
  d.setHours(12, 0, 0, 0);
  const end = new Date(fin);
  end.setHours(12, 0, 0, 0);
  while (d <= end) {
    const day = d.getDay();
    if (day !== 0 && day !== 6) n++;
    d.setDate(d.getDate() + 1);
  }
  return n;
}
