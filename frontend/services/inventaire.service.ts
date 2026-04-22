import { get, post, put } from "@/lib/api";
import type { PageResponse } from "@/lib/types/finance";
import type { BienRequest, BienResponse, MouvementResponse, StatsInventaireResponse } from "@/lib/types/inventaire";

export async function listBiens(params: {
  page?: number;
  size?: number;
  categorie?: string;
  etat?: string;
  localisation?: string;
}) {
  return get<PageResponse<BienResponse>>("inventaire/biens", { params });
}

export async function getBien(id: string) {
  return get<BienResponse>(`inventaire/biens/${id}`);
}

export async function creerBien(body: BienRequest) {
  return post<BienResponse>("inventaire/biens", body);
}

export async function modifierBien(id: string, body: BienRequest) {
  return put<BienResponse>(`inventaire/biens/${id}`, body);
}

export async function reformerBien(id: string, motif: string) {
  return post<void>(`inventaire/biens/${id}/reforme`, { motif });
}

export async function historiqueBien(id: string) {
  return get<MouvementResponse[]>(`inventaire/biens/${id}/historique`);
}

export async function statsInventaire() {
  return get<StatsInventaireResponse>("inventaire/stats");
}
