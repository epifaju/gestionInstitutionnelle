"use client";

import { create } from "zustand";
import { clearAccessTokenCookie, setAccessTokenCookie } from "./auth-cookie";

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
  setAuth: (user: UserInfo, token: string, expiresInSeconds?: number) => void;
  updateToken: (token: string, expiresInSeconds?: number) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
  hasRole: (role: string) => boolean;
  hasAnyRole: (roles: string[]) => boolean;
};

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  setAuth: (user, token, expiresInSeconds = 900) => {
    setAccessTokenCookie(token, expiresInSeconds);
    set({ user, accessToken: token });
  },
  updateToken: (token, expiresInSeconds = 900) => {
    setAccessTokenCookie(token, expiresInSeconds);
    set({ accessToken: token });
  },
  logout: () => {
    clearAccessTokenCookie();
    set({ user: null, accessToken: null });
  },
  isAuthenticated: () => !!get().accessToken,
  hasRole: (role) => get().user?.role === role,
  hasAnyRole: (roles) => !!get().user && roles.includes(get().user!.role),
}));
