import { get, put } from "@/lib/api";

export type TauxChangeResponse = {
  date: string; // YYYY-MM-DD
  devise: "EUR" | "XOF" | "USD" | string;
  tauxVersEur: string | number;
};

export type TauxChangeUpsertRequest = {
  date: string; // YYYY-MM-DD
  devise: "EUR" | "XOF" | "USD" | string;
  tauxVersEur: string | number;
};

export async function listTauxChange(date: string) {
  return get<TauxChangeResponse[]>("finance/taux-change", { params: { date } });
}

export async function upsertTauxChange(body: TauxChangeUpsertRequest) {
  return put<TauxChangeResponse>("finance/taux-change", body);
}

