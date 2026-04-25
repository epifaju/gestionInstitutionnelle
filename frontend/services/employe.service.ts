import { get, post } from "@/lib/api";
import type { PageResponse, CongeResponse, DroitsCongesDto, PaieResponse, SalarieResponse, DocumentUrlResponse } from "@/lib/types/rh";
import type { Notification } from "@/lib/types/notifications";

export function getEmployeProfil() {
  return get<SalarieResponse>("employe/profil");
}

export function listEmployeConges(params?: { page?: number; size?: number }) {
  return get<PageResponse<CongeResponse>>("employe/conges", { params });
}

export function getEmployeDroitsConges() {
  return get<DroitsCongesDto>("employe/droits-conges");
}

export function submitEmployeConge(body: { typeConge: string; dateDebut: string; dateFin: string; commentaire?: string | null }) {
  return post<CongeResponse>("employe/conges", body);
}

export function listEmployePaie(annee: number, params?: { page?: number; size?: number }) {
  return get<PageResponse<PaieResponse>>(`employe/paie/${annee}`, { params });
}

export function downloadEmployeFichePdf(annee: number, mois: number) {
  // backend returns PDF bytes (download), so use direct navigation
  return `/api/v1/employe/paie/${annee}/${mois}/fiche-pdf`;
}

export function listEmployeNotifications(params?: { nonLuesSeulement?: boolean; page?: number; size?: number }) {
  return get<{
    content: Notification[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  }>("employe/notifications", { params });
}

// Placeholder: backend endpoint "documents" to be added if needed
export function listEmployeDocuments() {
  return get<DocumentUrlResponse[]>("employe/documents");
}

