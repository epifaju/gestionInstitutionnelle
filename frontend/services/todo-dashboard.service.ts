import { get, post } from "@/lib/api";

export interface TodoActionItem {
  id: string;
  type: string;
  titre: string;
  sousTitre: string;
  statut: string;
  niveauUrgence: "CRITIQUE" | "URGENT" | "NORMAL";
  lienAction: string;
  dateCreation: string;
  dateEcheance: string | null;
  metadonnees: Record<string, unknown>;
}

export interface TodoActionGroup {
  categorie: string;
  label: string;
  icone: string;
  count: number;
  countUrgent: number;
  lienVoirTout: string;
  items: TodoActionItem[];
}

export interface TodoSection {
  roleLabel: string;
  icone: string;
  totalActions: number;
  actionsUrgentes: number;
  groupes: TodoActionGroup[];
}

export interface TodoDashboardResponse {
  generatedAt: string;
  roleUtilisateur: string;
  totalActionsGlobal: number;
  totalActionsUrgentes: number;
  sections: TodoSection[];
  comptages: Record<string, number>;
}

export interface QuickActionRequest {
  typeAction: string;
  commentaire?: string;
}

export interface QuickActionResponse {
  succes: boolean;
  message: string;
  nouveauStatut: string;
  nouvelleCountSection: number;
}

function safeJson(v: unknown): Record<string, unknown> {
  if (!v) return {};
  if (typeof v === "object") return v as Record<string, unknown>;
  if (typeof v === "string") {
    try {
      const parsed = JSON.parse(v) as unknown;
      if (parsed && typeof parsed === "object") return parsed as Record<string, unknown>;
    } catch {
      /* ignore */
    }
  }
  return {};
}

function normalizeDashboard(raw: TodoDashboardResponse): TodoDashboardResponse {
  const sections = (raw.sections ?? []).map((s) => ({
    ...s,
    groupes: (s.groupes ?? []).map((g) => ({
      ...g,
      items: (g.items ?? []).map((it) => ({
        ...it,
        metadonnees: safeJson((it as unknown as { metadonnees?: unknown }).metadonnees),
      })),
    })),
  }));
  return { ...raw, sections };
}

export const todoDashboardService = {
  async getTodoDashboard(): Promise<TodoDashboardResponse> {
    const raw = await get<TodoDashboardResponse>("todo-dashboard");
    return normalizeDashboard(raw);
  },

  async getComptages(): Promise<Record<string, number>> {
    return await get<Record<string, number>>("todo-dashboard/comptages");
  },

  async executerAction(itemType: string, itemId: string, req: QuickActionRequest): Promise<QuickActionResponse> {
    return await post<QuickActionResponse>(`todo-dashboard/action/${encodeURIComponent(itemType)}/${encodeURIComponent(itemId)}`, req);
  },
};

