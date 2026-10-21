import { api, get, post, put } from "@/lib/api";
import type { CourrierRequest, CourrierResponse } from "@/lib/types/courriers";
import type { PageResponse } from "@/lib/types/ged";

export async function listCourriers(params: {
  page?: number;
  size?: number;
  sens?: string;
  statut?: string;
  q?: string;
  from?: string;
  to?: string;
}) {
  return get<PageResponse<CourrierResponse>>("courriers", { params });
}

export async function getCourrier(id: string) {
  return get<CourrierResponse>(`courriers/${id}`);
}

export async function createCourrier(body: CourrierRequest) {
  return post<CourrierResponse>("courriers", body);
}

export async function updateCourrier(id: string, body: CourrierRequest) {
  return put<CourrierResponse>(`courriers/${id}`, body);
}

export async function changerStatutCourrier(id: string, statut: string) {
  const res = await api.patch<{ success: boolean; data: CourrierResponse }>(`courriers/${id}/statut`, {
    statut,
  });
  return res.data.data;
}

export async function rattacherDocumentCourrier(id: string, documentId: string) {
  return post<CourrierResponse>(`courriers/${id}/document`, { documentId });
}
