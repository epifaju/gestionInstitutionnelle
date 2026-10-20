const TYPE_CODES = [
  "CONTRAT",
  "FACTURE_JUS",
  "ORDRE_MISSION",
  "RAPPORT",
  "CIRCULAIRE",
  "NOTE_DIPLOMATIQUE",
  "ACREDITATION",
  "VISA",
  "PASSEPORT",
  "AUTRE",
  "COURRIER",
] as const;

const VISIBILITE_CODES = ["PRIVE", "SERVICE", "ORGANISATION", "PUBLIC"] as const;

export type DocumentTypeCode = (typeof TYPE_CODES)[number];
export type DocumentVisibiliteCode = (typeof VISIBILITE_CODES)[number];

export const DOCUMENT_UPLOAD_TYPES = TYPE_CODES.filter((c) => c !== "COURRIER");

export function isDocumentTypeCode(v: string): v is DocumentTypeCode {
  return (TYPE_CODES as readonly string[]).includes(v);
}

export function isDocumentVisibiliteCode(v: string): v is DocumentVisibiliteCode {
  return (VISIBILITE_CODES as readonly string[]).includes(v);
}
