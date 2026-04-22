import { api, get } from "@/lib/api";
import type { BilanMensuelCompletResponse } from "@/lib/types/dashboard";

export async function getBilanMensuel(annee: number, mois: number) {
  return get<BilanMensuelCompletResponse>(`rapports/bilan-mensuel/${annee}/${mois}`);
}

export async function downloadBilanMensuelPdf(annee: number, mois: number) {
  const res = await api.get(`rapports/bilan-mensuel/${annee}/${mois}/pdf`, { responseType: "blob" });
  return res.data as Blob;
}

export async function downloadBilanAnnuelExcel(annee: number) {
  const res = await api.get(`rapports/bilan-annuel/${annee}/excel`, { responseType: "blob" });
  return res.data as Blob;
}

export async function downloadExportCsv(entite: "factures" | "recettes" | "salaires") {
  const res = await api.get(`rapports/export-csv/${entite}`, { responseType: "blob" });
  return res.data as Blob;
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function downloadBlob(blob: Blob, filename: string) {
  triggerDownload(blob, filename);
}
