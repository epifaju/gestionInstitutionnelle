"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useAuthStore } from "@/lib/store";

export type AppTheme = "system" | "light" | "dark";

type ThemeContextValue = {
  theme: AppTheme;
  setTheme: (t: AppTheme) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

const LEGACY_STORAGE_KEY = "app_theme";
const STORAGE_KEY_PREFIX = "app_theme:";

function storageKeyForUser(userId: string | null | undefined) {
  return `${STORAGE_KEY_PREFIX}${userId ?? "anon"}`;
}

function applyThemeToDom(theme: AppTheme) {
  if (typeof window === "undefined") return;
  const root = document.documentElement;
  const prefersDark = window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
  const shouldDark = theme === "dark" || (theme === "system" && prefersDark);
  root.classList.toggle("dark", shouldDark);
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const userId = useAuthStore((s) => s.user?.id);
  const [theme, setThemeState] = useState<AppTheme>("system");
  const activeKeyRef = useRef<string>(storageKeyForUser(userId));

  useEffect(() => {
    try {
      // Ensure legacy global key doesn't leak across users.
      localStorage.removeItem(LEGACY_STORAGE_KEY);

      const key = storageKeyForUser(userId);
      activeKeyRef.current = key;
      const s = localStorage.getItem(key);
      if (s === "system" || s === "light" || s === "dark") {
        setThemeState(s);
        applyThemeToDom(s);
      } else {
        setThemeState("system");
        applyThemeToDom("system");
      }
    } catch {
      setThemeState("system");
      applyThemeToDom("system");
    }
  }, [userId]);

  useEffect(() => {
    // Keep in sync when system theme changes and user chose 'system'
    const mq = window.matchMedia?.("(prefers-color-scheme: dark)");
    if (!mq) return;
    const handler = () => {
      if (theme === "system") applyThemeToDom("system");
    };
    mq.addEventListener?.("change", handler);
    return () => mq.removeEventListener?.("change", handler);
  }, [theme]);

  const setTheme = useCallback((t: AppTheme) => {
    setThemeState(t);
    applyThemeToDom(t);
    try {
      localStorage.setItem(activeKeyRef.current, t);
    } catch {
      /* ignore */
    }
  }, []);

  const value = useMemo(() => ({ theme, setTheme }), [theme, setTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useAppTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useAppTheme must be used within ThemeProvider");
  return ctx;
}

