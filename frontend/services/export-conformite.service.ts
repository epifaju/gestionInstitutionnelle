import { api, del, get, post, put } from "@/lib/api";
import type { PageResponse } from "@/lib/types/rh";

export type TypeExport =
  | "NOTE_FRAIS_PDF"
  | "ETAT_PAIE_PDF"
  | "ETAT_PAIE_EXCEL"
  | "BUDGET_PREVISIONNEL_PDF"
  | "BUDGET_PREVISIONNEL_EXCEL"
  | "JOURNAL_AUDIT_PDF"
  | "JOURNAL_AUDIT_EXCEL"
  | "JOURNAL_AUDIT_CSV";

export interface ExportJobResponse {
  id: string | null;
  typeExport: TypeExport;
  statut: "EN_ATTENTE" | "EN_COURS" | "TERMINE" | "ERREUR" | "EXPIRE";
  progression: number;
  fichierUrl: string | null;
  nomFichier: string | null;
  tailleOctets: number | null;
  nbLignes: number | null;
  messageErreur: string | null;
  expireA: string | null;
  createdAt: string | null;
}

export type ExportEtatPaieParams = {
  annee: number;
  mois: number;
  service?: string | null;
};

export type ExportJournalParams = {
  dateDebut: string; // YYYY-MM-DD
  dateFin: string; // YYYY-MM-DD
  entite?: string | null;
  action?: string | null;
  utilisateurId?: string | null;
};

export type ConfigExport = {
  id?: string;
  organisationId?: string;
  logoUrl?: string | null;
  piedPageMention?: string | null;
  couleurPrincipale?: string | null;
  seuilLignesSyncPdf?: number | null;
  seuilLignesSyncExcel?: number | null;
  watermarkActif?: boolean | null;
  watermarkTexte?: string | null;
  signatureDgUrl?: string | null;
  cachetOrgUrl?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

const base = "exports";

export async function exportNoteFrais(missionId: string): Promise<ExportJobResponse> {
  return await post<ExportJobResponse>(`${base}/notes-frais`, { missionId });
}

export async function exportEtatPaiePdf(params: ExportEtatPaieParams): Promise<ExportJobResponse> {
  return await post<ExportJobResponse>(`${base}/etats-paie/pdf`, params);
}

export async function exportEtatPaieExcel(params: ExportEtatPaieParams): Promise<ExportJobResponse> {
  return await post<ExportJobResponse>(`${base}/etats-paie/excel`, params);
}

export async function exportBudgetPdf(annee: number): Promise<ExportJobResponse> {
  return await post<ExportJobResponse>(`${base}/budget/pdf`, { annee });
}

export async function exportBudgetExcel(annee: number): Promise<ExportJobResponse> {
  return await post<ExportJobResponse>(`${base}/budget/excel`, { annee });
}

export async function exportJournalAuditPdf(params: ExportJournalParams): Promise<ExportJobResponse> {
  return await post<ExportJobResponse>(`${base}/journal-audit/pdf`, params);
}

export async function exportJournalAuditExcel(params: ExportJournalParams): Promise<ExportJobResponse> {
  return await post<ExportJobResponse>(`${base}/journal-audit/excel`, params);
}

/**
 * Endpoint CSV retourne un stream (pas ApiResponse). On le transforme en "job" TERMINE
 * en créant un Blob URL local afin de réutiliser les composants.
 */
export async function exportJournalAuditCsv(params: ExportJournalParams): Promise<ExportJobResponse> {
  const res = await api.post(`${base}/journal-audit/csv`, params, { responseType: "blob" });
  const blob = res.data as Blob;
  const url = URL.createObjectURL(blob);
  const dispo = String(res.headers?.["content-disposition"] ?? "");
  const m = dispo.match(/filename="([^"]+)"/i);
  const filename = m?.[1] ?? "journal-audit.csv";
  return {
    id: null,
    typeExport: "JOURNAL_AUDIT_CSV",
    statut: "TERMINE",
    progression: 100,
    fichierUrl: url,
    nomFichier: filename,
    tailleOctets: blob.size,
    nbLignes: null,
    messageErreur: null,
    expireA: null,
    createdAt: new Date().toISOString(),
  };
}

export async function getJob(jobId: string): Promise<ExportJobResponse> {
  return await get<ExportJobResponse>(`${base}/jobs/${jobId}`);
}

export async function getMesJobs(page = 0): Promise<PageResponse<ExportJobResponse>> {
  return await get<PageResponse<ExportJobResponse>>(`${base}/jobs?page=${page}&size=20`);
}

export async function supprimerJob(jobId: string): Promise<void> {
  await del<void>(`${base}/jobs/${jobId}`);
}

export async function getConfig(): Promise<ConfigExport> {
  return await get<ConfigExport>(`${base}/config`);
}

export async function updateConfig(config: Partial<ConfigExport>): Promise<ConfigExport> {
  return await put<ConfigExport>(`${base}/config`, config);
}

export async function uploadLogo(file: File): Promise<string> {
  const fd = new FormData();
  fd.append("logo", file);
  // Important: laisser axios définir le boundary
  const res = await api.post<{ success: boolean; data: string }>(`${base}/config/logo`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function uploadCachet(file: File): Promise<string> {
  const fd = new FormData();
  fd.append("cachet", file);
  const res = await api.post<{ success: boolean; data: string }>(`${base}/config/cachet`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function uploadSignatureDg(file: File): Promise<string> {
  const fd = new FormData();
  fd.append("signature", file);
  const res = await api.post<{ success: boolean; data: string }>(`${base}/config/signature-dg`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

