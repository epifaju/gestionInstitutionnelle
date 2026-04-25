import { api, get, post, put } from "@/lib/api";
import type { FraisRequest, FraisResponse, MissionRequest, MissionResponse } from "@/lib/types/missions";
import type { PageResponse } from "@/lib/types/finance";

export async function listMissions(params: {
  page?: number;
  size?: number;
  statut?: string;
  salarieId?: string;
  debut?: string;
  fin?: string;
}) {
  return get<PageResponse<MissionResponse>>("missions", { params });
}

export async function getMission(id: string) {
  return get<MissionResponse>(`missions/${id}`);
}

export async function createMission(body: MissionRequest) {
  return post<MissionResponse>("missions", body);
}

export async function updateMission(id: string, body: MissionRequest) {
  return put<MissionResponse>(`missions/${id}`, body);
}

export async function soumettreMission(id: string) {
  return post<MissionResponse>(`missions/${id}/soumettre`);
}

export async function approuverMission(id: string, avanceVersee: number | null) {
  return post<MissionResponse>(`missions/${id}/approuver`, avanceVersee != null ? { avanceVersee } : {});
}

export async function refuserMission(id: string, motifRefus: string) {
  return post<MissionResponse>(`missions/${id}/refuser`, { motifRefus });
}

export async function terminerMission(id: string) {
  return post<MissionResponse>(`missions/${id}/terminer`);
}

export async function uploadOrdreMission(id: string, file: File) {
  const fd = new FormData();
  fd.append("file", file);
  const res = await api.post<{ success: boolean; data: { url: string } }>(`missions/${id}/ordre-mission`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data.url;
}

export async function uploadRapportMission(id: string, file: File) {
  const fd = new FormData();
  fd.append("file", file);
  const res = await api.post<{ success: boolean; data: { url: string } }>(`missions/${id}/rapport`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data.url;
}

export async function ajouterFrais(missionId: string, req: FraisRequest, justificatif?: File | null) {
  const fd = new FormData();
  fd.append("req", new Blob([JSON.stringify(req)], { type: "application/json" }));
  if (justificatif) fd.append("justificatif", justificatif);
  const res = await api.post<{ success: boolean; data: FraisResponse }>(`missions/${missionId}/frais`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function validerFrais(missionId: string, fraisId: string) {
  return post<FraisResponse>(`missions/${missionId}/frais/${fraisId}/valider`);
}

export async function rembourserFrais(missionId: string, fraisId: string) {
  return post<FraisResponse>(`missions/${missionId}/frais/${fraisId}/rembourser`);
}

