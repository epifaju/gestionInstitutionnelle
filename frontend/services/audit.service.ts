import { get } from "@/lib/api";
import type { PageResponse } from "@/lib/types/rh";

export type AuditLogResponse = {
  id: string;
  utilisateurEmail: string | null;
  utilisateurRole: string | null;
  action: string;
  entite: string;
  entiteId: string | null;
  avant: unknown | null;
  apres: unknown | null;
  ipAddress: string | null;
  dateAction: string | null;
};

export async function listAuditLogs(params: {
  page?: number;
  size?: number;
  dateDebut: string; // YYYY-MM-DD
  dateFin: string; // YYYY-MM-DD
  entite?: string | null;
  action?: string | null;
  utilisateurId?: string | null;
}): Promise<PageResponse<AuditLogResponse>> {
  const { page = 0, size = 20, ...rest } = params;
  const qs = new URLSearchParams();
  qs.set("page", String(page));
  qs.set("size", String(size));
  qs.set("dateDebut", rest.dateDebut);
  qs.set("dateFin", rest.dateFin);
  if (rest.entite) qs.set("entite", rest.entite);
  if (rest.action) qs.set("action", rest.action);
  if (rest.utilisateurId) qs.set("utilisateurId", rest.utilisateurId);
  return await get<PageResponse<AuditLogResponse>>(`audit-logs?${qs.toString()}`);
}

export async function countAuditLogs(params: {
  dateDebut: string;
  dateFin: string;
  entite?: string | null;
  action?: string | null;
  utilisateurId?: string | null;
}): Promise<number> {
  const qs = new URLSearchParams();
  qs.set("dateDebut", params.dateDebut);
  qs.set("dateFin", params.dateFin);
  if (params.entite) qs.set("entite", params.entite);
  if (params.action) qs.set("action", params.action);
  if (params.utilisateurId) qs.set("utilisateurId", params.utilisateurId);
  const res = await get<{ count: number }>(`audit-logs/count?${qs.toString()}`);
  return Number(res?.count ?? 0);
}

