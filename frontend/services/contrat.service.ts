import { api, get, post } from "@/lib/api";
import type { PageResponse } from "@/lib/types/rh";
import type {
  ContratRequest,
  ContratResponse,
  DecisionFinCddRequest,
  EcheanceDashboardResponse,
  EcheanceRequest,
  EcheanceResponse,
  FormationObligatoireRequest,
  FormationObligatoireResponse,
  RenouvellementCddRequest,
  TitreSejourRequest,
  TitreSejourResponse,
  TraiterEcheanceRequest,
  VisiteMedicaleRequest,
  VisiteMedicaleResponse,
} from "@/types/contrat.types";

const BASE = "rh/contrats";

export async function getContrats(params: {
  page?: number;
  size?: number;
  typeContrat?: string;
  service?: string;
  sort?: string | string[];
}) {
  return get<PageResponse<ContratResponse>>(BASE, { params });
}

export async function getContratActif(salarieId: string) {
  return get<ContratResponse>(`${BASE}/salaries/${salarieId}/actif`);
}

export async function getHistoriqueContrats(salarieId: string) {
  return get<ContratResponse[]>(`${BASE}/salaries/${salarieId}/historique`);
}

export async function creerContrat(salarieId: string, data: ContratRequest) {
  return post<ContratResponse>(`${BASE}/salaries/${salarieId}`, data);
}

export async function renouvelerCdd(contratId: string, data: RenouvellementCddRequest) {
  return post<ContratResponse>(`${BASE}/${contratId}/renouveler`, data);
}

export async function enregistrerDecisionFin(contratId: string, data: DecisionFinCddRequest) {
  return post<ContratResponse>(`${BASE}/${contratId}/decision-fin`, data);
}

export async function uploadContratSigne(contratId: string, file: File) {
  const fd = new FormData();
  fd.append("file", file);
  const res = await api.post<{ success: boolean; data: string }>(`${BASE}/${contratId}/contrat-signe`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function getCddExpirant(jours = 90) {
  return get<ContratResponse[]>(`${BASE}/cdd-expirant`, { params: { jours } });
}

export async function getEcheances(params: {
  page?: number;
  size?: number;
  statut?: string;
  type?: string;
  salarieId?: string;
  dateMin?: string;
  dateMax?: string;
  sort?: string | string[];
}) {
  return get<PageResponse<EcheanceResponse>>(`${BASE}/echeances`, { params });
}

export async function getEcheancesDashboard() {
  return get<EcheanceDashboardResponse>(`${BASE}/echeances/dashboard`);
}

export async function creerEcheance(data: EcheanceRequest) {
  return post<EcheanceResponse>(`${BASE}/echeances`, data);
}

export async function traiterEcheance(id: string, data: TraiterEcheanceRequest, preuve?: File | null) {
  const fd = new FormData();
  fd.append(
    "data",
    new Blob([JSON.stringify(data)], { type: "application/json" }),
    "data.json"
  );
  if (preuve) fd.append("preuve", preuve);
  const res = await api.post<{ success: boolean; data: EcheanceResponse }>(`${BASE}/echeances/${id}/traiter`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function annulerEcheance(id: string, motif: string) {
  return post<EcheanceResponse>(`${BASE}/echeances/${id}/annuler`, null, { params: { motif } });
}

export async function getVisitesSalarie(salarieId: string) {
  return get<VisiteMedicaleResponse[]>(`${BASE}/salaries/${salarieId}/visites`);
}

export async function creerVisite(salarieId: string, data: VisiteMedicaleRequest) {
  return post<VisiteMedicaleResponse>(`${BASE}/salaries/${salarieId}/visites`, data);
}

export async function enregistrerResultatVisite(
  id: string,
  resultat: string,
  restrictions?: string | null,
  compteRendu?: File | null
) {
  const fd = new FormData();
  fd.append("resultat", resultat);
  if (restrictions != null && restrictions !== "") fd.append("restrictions", restrictions);
  if (compteRendu) fd.append("compteRendu", compteRendu);
  const res = await api.post<{ success: boolean; data: VisiteMedicaleResponse }>(`${BASE}/visites/${id}/resultat`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function getTitresSejour(salarieId: string) {
  return get<TitreSejourResponse[]>(`${BASE}/salaries/${salarieId}/titres-sejour`);
}

export async function enregistrerTitreSejour(salarieId: string, data: TitreSejourRequest, document?: File | null) {
  const fd = new FormData();
  fd.append("data", new Blob([JSON.stringify(data)], { type: "application/json" }), "data.json");
  if (document) fd.append("document", document);
  const res = await api.post<{ success: boolean; data: TitreSejourResponse }>(`${BASE}/salaries/${salarieId}/titres-sejour`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

/** Endpoint complémentaire utilisé par l’UI (statut de suivi administratif). */
export async function mettreAJourStatutRenouvellementTitre(id: string, statut: string) {
  const res = await api.put<{ success: boolean; data: TitreSejourResponse }>(`${BASE}/titres-sejour/${id}/statut-renouvellement`, null, {
    params: { statut },
  });
  return res.data.data;
}

export async function getFormations(salarieId: string) {
  return get<FormationObligatoireResponse[]>(`${BASE}/salaries/${salarieId}/formations`);
}

export async function enregistrerFormation(salarieId: string, data: FormationObligatoireRequest, certificat?: File | null) {
  const fd = new FormData();
  fd.append("data", new Blob([JSON.stringify(data)], { type: "application/json" }), "data.json");
  if (certificat) fd.append("certificat", certificat);
  const res = await api.post<{ success: boolean; data: FormationObligatoireResponse }>(`${BASE}/salaries/${salarieId}/formations`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

/** Endpoint complémentaire utilisé par l’UI (renouvellement d’une formation existante). */
export async function renouvelerFormation(id: string, data: FormationObligatoireRequest, certificat?: File | null) {
  const fd = new FormData();
  fd.append("data", new Blob([JSON.stringify(data)], { type: "application/json" }), "data.json");
  if (certificat) fd.append("certificat", certificat);
  const res = await api.post<{ success: boolean; data: FormationObligatoireResponse }>(`${BASE}/formations/${id}/renouveler`, fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}
