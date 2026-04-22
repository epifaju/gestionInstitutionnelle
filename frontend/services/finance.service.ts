import { api, get, post, put } from "@/lib/api";
import type {
  CategorieRequest,
  CategorieResponse,
  FactureRequest,
  FactureResponse,
  PageResponse,
  PaiementRequest,
  PaiementResponse,
  RecetteRequest,
  RecetteResponse,
  StatsResponse,
} from "@/lib/types/finance";

export async function listFactures(params: {
  page?: number;
  size?: number;
  statut?: string;
  categorieId?: string;
  debut?: string;
  fin?: string;
  fournisseur?: string;
  montantMin?: number;
  montantMax?: number;
}) {
  return get<PageResponse<FactureResponse>>("finance/factures", { params });
}

export async function getFacture(id: string) {
  return get<FactureResponse>(`finance/factures/${id}`);
}

export async function createFacture(req: FactureRequest, justificatif?: File | null) {
  const fd = new FormData();
  fd.append("facture", new Blob([JSON.stringify(req)], { type: "application/json" }));
  if (justificatif) fd.append("justificatif", justificatif);
  const res = await api.post<{ success: boolean; data: FactureResponse }>("finance/factures", fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function updateFacture(id: string, body: FactureRequest) {
  return put<FactureResponse>(`finance/factures/${id}`, body);
}

export async function changerStatutFacture(id: string, nouveauStatut: string) {
  return put<FactureResponse>(`finance/factures/${id}/statut`, { nouveauStatut });
}

export async function uploadJustificatifFacture(id: string, file: File) {
  const fd = new FormData();
  fd.append("file", file);
  await api.post(`finance/factures/${id}/justificatif`, fd, {
    headers: { "Content-Type": undefined },
  });
}

export async function enregistrerPaiement(body: PaiementRequest) {
  return post<PaiementResponse>("finance/paiements", body);
}

export async function listPaiements(params?: { page?: number; size?: number }) {
  return get<PageResponse<PaiementResponse>>("finance/paiements", { params });
}

export async function listRecettes(params: { page?: number; size?: number; type?: string; debut?: string; fin?: string }) {
  return get<PageResponse<RecetteResponse>>("finance/recettes", { params });
}

export async function createRecette(req: RecetteRequest, justificatif?: File | null) {
  const fd = new FormData();
  fd.append("recette", new Blob([JSON.stringify(req)], { type: "application/json" }));
  if (justificatif) fd.append("justificatif", justificatif);
  const res = await api.post<{ success: boolean; data: RecetteResponse }>("finance/recettes", fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function updateRecette(id: string, body: RecetteRequest) {
  return put<RecetteResponse>(`finance/recettes/${id}`, body);
}

export async function deleteRecette(id: string) {
  return api.delete(`finance/recettes/${id}`);
}

export async function uploadJustificatifRecette(id: string, file: File) {
  const fd = new FormData();
  fd.append("file", file);
  await api.post(`finance/recettes/${id}/justificatif`, fd, {
    headers: { "Content-Type": undefined },
  });
}

export async function getStats(annee: number, mois: number) {
  return get<StatsResponse>(`finance/stats/${annee}/${mois}`);
}

export async function listCategories() {
  return get<CategorieResponse[]>("finance/categories");
}

export async function listCategoriesAdmin(params?: { includeInactive?: boolean }) {
  return get<CategorieResponse[]>("finance/categories", { params });
}

export async function createCategorie(body: CategorieRequest) {
  return post<CategorieResponse>("finance/categories", body);
}

export async function updateCategorie(id: string, body: CategorieRequest) {
  return put<CategorieResponse>(`finance/categories/${id}`, body);
}

export async function deleteCategorie(id: string) {
  return api.delete(`finance/categories/${id}`);
}

export async function reactivateCategorie(id: string) {
  return api.post(`finance/categories/${id}/reactiver`);
}
