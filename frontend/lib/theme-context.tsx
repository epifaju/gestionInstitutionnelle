"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export type AppTheme = "system" | "light" | "dark";

type ThemeContextValue = {
  theme: AppTheme;
  setTheme: (t: AppTheme) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

const STORAGE_KEY = "app_theme";

function applyThemeToDom(theme: AppTheme) {
  if (typeof window === "undefined") return;
  const root = document.documentElement;
  const prefersDark = window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
  const shouldDark = theme === "dark" || (theme === "system" && prefersDark);
  root.classList.toggle("dark", shouldDark);
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<AppTheme>("system");

  useEffect(() => {
    try {
      const s = localStorage.getItem(STORAGE_KEY);
      if (s === "system" || s === "light" || s === "dark") {
        setThemeState(s);
        applyThemeToDom(s);
      } else {
        applyThemeToDom("system");
      }
    } catch {
      applyThemeToDom("system");
    }
  }, []);

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
      localStorage.setItem(STORAGE_KEY, t);
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

