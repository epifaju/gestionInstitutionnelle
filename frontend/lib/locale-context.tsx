"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export type AppLocale = "fr" | "en" | "pt-PT";

type LocaleContextValue = {
  locale: AppLocale;
  setLocale: (l: AppLocale) => void;
};

const LocaleContext = createContext<LocaleContextValue | null>(null);

const STORAGE_KEY = "app_locale";

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<AppLocale>("fr");

  useEffect(() => {
    try {
      const s = localStorage.getItem(STORAGE_KEY);
      if (s === "en" || s === "fr" || s === "pt-PT") {
        setLocaleState(s);
        return;
      }
      // fallback cookie (optional)
      const m = document.cookie.match(/(?:^|;\s*)locale=([^;]+)/);
      const v = m ? decodeURIComponent(m[1]) : "";
      if (v === "en" || v === "fr" || v === "pt-PT") {
        setLocaleState(v);
      }
    } catch {
      /* ignore */
    }
  }, []);

  const setLocale = useCallback((l: AppLocale) => {
    setLocaleState(l);
    try {
      localStorage.setItem(STORAGE_KEY, l);
    } catch {
      /* ignore */
    }
  }, []);

  const value = useMemo(() => ({ locale, setLocale }), [locale, setLocale]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useAppLocale() {
  const ctx = useContext(LocaleContext);
  if (!ctx) {
    throw new Error("useAppLocale must be used within LocaleProvider");
  }
  return ctx;
}
