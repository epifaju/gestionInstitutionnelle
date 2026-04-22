import axios, { AxiosError, AxiosRequestConfig } from "axios";
import { toast } from "sonner";
import { useAuthStore } from "./store";

function buildBaseUrl(): string {
  const env = process.env.NEXT_PUBLIC_API_URL || "/api";
  const trimmed = env.replace(/\/$/, "");
  return `${trimmed}/v1`;
}

const baseURL =
  typeof window !== "undefined"
    ? buildBaseUrl()
    : (process.env.INTERNAL_API_URL || process.env.NEXT_PUBLIC_API_URL || "http://backend:8080/api").replace(
        /\/$/,
        ""
      ) + "/v1";

export const api = axios.create({
  baseURL,
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = api
      .post<{
        success: boolean;
        data?: { accessToken: string; expiresIn: number };
      }>("auth/refresh")
      .then((res) => {
        const token = res.data?.data?.accessToken;
        const expiresIn = res.data?.data?.expiresIn ?? 900;
        if (token) {
          useAuthStore.getState().updateToken(token, expiresIn);
        }
        return token ?? null;
      })
      .catch(() => {
        useAuthStore.getState().logout();
        if (typeof window !== "undefined") {
          window.location.href = "/login";
        }
        return null;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as AxiosRequestConfig & { _retry?: boolean };
    const status = error.response?.status;
    const url = original?.url ?? "";
    if (status === 401 && !original._retry && !url.includes("auth/refresh") && !url.includes("auth/login")) {
      original._retry = true;
      const newToken = await refreshAccessToken();
      if (newToken) {
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      }
    }

    if (typeof window !== "undefined") {
      const silentPath =
        url.includes("auth/login") ||
        url.includes("auth/refresh") ||
        url.includes("auth/forgot-password") ||
        url.includes("auth/reset-password");
      const st = error.response?.status;
      if (!silentPath && st != null && st >= 400 && st !== 401) {
        const data = error.response?.data as
          | { message?: string; code?: string; fieldErrors?: Record<string, string> }
          | undefined;
        const code = data?.code;

        // Certaines erreurs sont attendues et gérées par l'UI (ex: "pas de budget pour l'année")
        // → on évite un toast générique inutile.
        if (code === "BUDGET_ABSENT") {
          return Promise.reject(error);
        }

        if (code === "SALAIRE_MODIF_VIA_GRILLE") {
          toast.error("Le salaire ne peut pas être modifié ici. Utilisez « Nouvelle grille » (historique salarial).");
          return Promise.reject(error);
        }

        if (code === "TAUX_CHANGE_ABSENT") {
          toast.error(data?.message ?? "Taux de change manquant — renseignez-le dans Finance → Taux de change (/finance/taux-change).");
          return Promise.reject(error);
        }
        let msg = data?.message ?? error.message;
        if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
          const first = Object.values(data.fieldErrors)[0];
          msg = data?.code ? `${data.code}: ${first}` : first;
        } else if (data?.code && data?.message) {
          msg = `${data.code} — ${data.message}`;
        } else if (data?.code) {
          msg = data.code;
        }
        toast.error(msg);
      }
    }

    return Promise.reject(error);
  }
);

export async function get<T>(url: string, config?: AxiosRequestConfig) {
  const res = await api.get<{ success: boolean; data: T }>(url, config);
  return res.data.data;
}

export async function post<T>(url: string, body?: unknown, config?: AxiosRequestConfig) {
  const res = await api.post<{ success: boolean; data: T }>(url, body, config);
  return res.data.data;
}

export async function put<T>(url: string, body?: unknown, config?: AxiosRequestConfig) {
  const res = await api.put<{ success: boolean; data: T }>(url, body, config);
  return res.data.data;
}

export async function del<T>(url: string, config?: AxiosRequestConfig) {
  const res = await api.delete<{ success: boolean; data: T }>(url, config);
  return res.data.data;
}
