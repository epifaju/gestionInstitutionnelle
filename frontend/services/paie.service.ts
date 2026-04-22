import { get, put } from "@/lib/api";
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
