import { get } from "@/lib/api";

export async function convertirDevise(params: { montant: number; de: string; vers: string; date?: string }) {
  return get<{ montant: number; de: string; vers: string; date: string | null; taux: number; resultat: number }>("devises/convertir", {
    params,
  });
}

export async function tauxDuJour(base: string) {
  return get<Record<string, number>>("devises/taux-du-jour", { params: { base } });
}

export async function historiqueTaux(params: { devise1: string; devise2: string; debut?: string; fin?: string }) {
  const { devise1, devise2, ...rest } = params;
  return get<
    {
      id: string;
      deviseBase: string;
      deviseCible: string;
      taux: number;
      dateTaux: string;
      source: string;
      createdAt: string;
    }[]
  >(`devises/historique/${devise1}/${devise2}`, { params: rest });
}

