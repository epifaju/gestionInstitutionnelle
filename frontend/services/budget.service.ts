import axios from "axios";
import { get, post, put } from "@/lib/api";
import type { BudgetRequest, BudgetResponse } from "@/lib/types/budget";

export async function getBudget(annee: number): Promise<BudgetResponse | null> {
  try {
    return await get<BudgetResponse>(`budget/${annee}`);
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) return null;
    throw e;
  }
}

export async function creerBudget(body: BudgetRequest) {
  return post<BudgetResponse>("budget", body);
}

export async function modifierLigneBudget(budgetId: string, ligneId: string, montantPrevu: string) {
  return put<BudgetResponse>(`budget/${budgetId}/ligne/${ligneId}`, { montantPrevu });
}

export async function validerBudget(budgetId: string) {
  return post<BudgetResponse>(`budget/${budgetId}/valider`, {});
}
