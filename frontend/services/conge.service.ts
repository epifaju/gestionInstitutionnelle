import { get, post } from "@/lib/api";
import type { CongeRequest, CongeResponse, CongeValidationRequest, PageResponse } from "@/lib/types/rh";

export async function listConges(params: {
  page?: number;
  size?: number;
  statut?: string;
  typeConge?: string;
  debut?: string;
  fin?: string;
  service?: string;
  salarieId?: string;
}) {
  return get<PageResponse<CongeResponse>>("rh/conges", { params });
}

export async function soumettreConge(body: CongeRequest) {
  return post<CongeResponse>("rh/conges", body);
}

export async function getCalendrier(debut: string, fin: string) {
  return get<CongeResponse[]>("rh/conges/calendrier", { params: { debut, fin } });
}

export async function validerConge(id: string) {
  return post<CongeResponse>(`rh/conges/${id}/valider`);
}

export async function rejeterConge(id: string, body: CongeValidationRequest) {
  return post<CongeResponse>(`rh/conges/${id}/rejeter`, body);
}

/** Annule un congé déjà validé (date de début future uniquement). */
export async function annulerCongeValide(id: string) {
  return post<CongeResponse>(`rh/conges/${id}/annuler`);
}
