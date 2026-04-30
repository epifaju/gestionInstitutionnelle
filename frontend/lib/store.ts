"use client";

import { create } from "zustand";
import { clearLocaleCookie } from "./locale-cookie";

export type UserInfo = {
  id: string;
  email: string;
  nom: string | null;
  prenom: string | null;
  role: string;
  organisationId: string;
  organisationNom: string | null;
  langue?: string | null;
};

export type UserPreferences = {
  theme: "system" | "light" | "dark";
  notificationsUiEnabled: string[];
  notificationsEmailEnabled: string[];
};

type AuthState = {
  user: UserInfo | null;
  accessToken: string | null;
  setAuth: (user: UserInfo, token: string) => void;
  updateToken: (token: string) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
  hasRole: (role: string) => boolean;
  hasAnyRole: (roles: string[]) => boolean;
};

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  setAuth: (user, token) => {
    set({ user, accessToken: token });
  },
  updateToken: (token) => {
    set({ accessToken: token });
  },
  logout: () => {
    try {
      // Legacy global theme key could leak between accounts in same browser.
      localStorage.removeItem("app_theme");
      // Évite que la langue du dernier compte connecté "déteigne" sur le suivant.
      localStorage.removeItem("app_locale");
    } catch {
      /* ignore */
    }
    try {
      clearLocaleCookie();
    } catch {
      /* ignore */
    }
    set({ user: null, accessToken: null });
  },
  isAuthenticated: () => !!get().accessToken,
  hasRole: (role) => get().user?.role === role,
  hasAnyRole: (roles) => !!get().user && roles.includes(get().user!.role),
}));
