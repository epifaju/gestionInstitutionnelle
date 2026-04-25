import { get, post, put } from "@/lib/api";
import type { MarquerPayeRequest, PageResponse, PaieResponse } from "@/lib/types/rh";

export async function listPaieOrganisation(annee: number, params?: { page?: number; size?: number }) {
  return get<PageResponse<PaieResponse>>("rh/paie", { params: { annee, ...params } });
}

export async function getPaieAnnuelle(salarieId: string, annee: number, params?: { page?: number; size?: number }) {
  return get<PageResponse<PaieResponse>>(`rh/paie/${salarieId}/${annee}`, { params });
}

export async function marquerPaye(id: string, body: MarquerPayeRequest) {
  return put<PaieResponse>(`rh/paie/${id}/marquer-paye`, body);
}

export async function annulerPaie(id: string) {
  return post<PaieResponse>(`rh/paie/${id}/annuler`);
}

export async function listMyPaie(annee: number, params?: { page?: number; size?: number }) {
  return get<PageResponse<PaieResponse>>("rh/me/paie", { params: { annee, ...params } });
}

export async function getMyPayslipPresignedUrl(annee: number, mois: number) {
  return get<{ url: string }>(`rh/me/paie/${annee}/${mois}/bulletin-url`);
}

export async function getPayslipPresignedUrlForSalarie(salarieId: string, annee: number, mois: number) {
  return get<{ url: string }>(`rh/paie/${salarieId}/${annee}/${mois}/bulletin-url`);
}
