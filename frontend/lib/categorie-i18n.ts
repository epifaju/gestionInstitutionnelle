/** Normalize a category label for i18n lookup (French seed / user data). */
export function slugCategorieLibelle(libelle: string) {
  return libelle
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_|_$/g, "");
}

type CategoryLabelsT = {
  (key: string): string;
  has: (key: string) => boolean;
};

/** Translate a finance category using code then known French libellé; fallback to stored libellé. */
export function translateCategorieLabel(
  t: CategoryLabelsT,
  cat: { code?: string | null; libelle?: string | null }
): string {
  const libelle = (cat.libelle ?? "").trim();
  const code = (cat.code ?? "").trim().toUpperCase();

  if (code && t.has(`byCode.${code}`)) {
    return t(`byCode.${code}`);
  }

  if (libelle) {
    const slug = slugCategorieLibelle(libelle);
    if (slug && t.has(`byLibelle.${slug}`)) {
      return t(`byLibelle.${slug}`);
    }
  }

  return libelle || code || "—";
}
