import { api, get, post, put } from "@/lib/api";
import type {
  DocumentUrlResponse,
  DroitsCongesDto,
  HistoriqueSalaireResponse,
  PageResponse,
  SalarieRequest,
  SalarieResponse,
} from "@/lib/types/rh";

export async function listSalaries(params: {
  page?: number;
  size?: number;
  statut?: string;
  service?: string;
  search?: string;
}) {
  return get<PageResponse<SalarieResponse>>("rh/salaries", { params });
}

export async function getSalarie(id: string) {
  return get<SalarieResponse>(`rh/salaries/${id}`);
}

export async function getMySalarie() {
  return get<SalarieResponse>("rh/me/salarie");
}

export async function createSalarie(body: SalarieRequest) {
  return post<SalarieResponse>("rh/salaries", body);
}

export async function updateSalarie(id: string, body: SalarieRequest) {
  return put<SalarieResponse>(`rh/salaries/${id}`, body);
}

export async function validerDossier(id: string) {
  return post<SalarieResponse>(`rh/salaries/${id}/valider`);
}

export async function uploadContrat(id: string, file: File) {
  const fd = new FormData();
  fd.append("file", file);
  const res = await api.post<{ success: boolean; data: string }>(`rh/salaries/${id}/documents`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function listDocuments(id: string) {
  return get<DocumentUrlResponse[]>(`rh/salaries/${id}/documents`);
}

export async function listHistoriqueSalaires(id: string) {
  return get<HistoriqueSalaireResponse[]>(`rh/salaries/${id}/historique-salaires`);
}

export async function getDroitsConges(salarieId: string, annee: number) {
  return get<DroitsCongesDto>(`rh/salaries/${salarieId}/droits-conges/${annee}`);
}

export async function ajouterGrilleSalariale(
  id: string,
  body: { brut: number; net: number; devise: string; dateDebut: string }
) {
  return post<SalarieResponse>(`rh/salaries/${id}/grille-salariale`, body);
}
