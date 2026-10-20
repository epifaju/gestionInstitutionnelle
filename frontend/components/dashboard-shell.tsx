"use client";

import dynamic from "next/dynamic";
import { useAuthStore, type UserInfo } from "@/lib/store";
import { api, get } from "@/lib/api";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Building2, LogOut, Menu, X, Languages } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTranslations } from "next-intl";
import { useAppLocale } from "@/lib/locale-context";
import type { AppLocale } from "@/lib/locale-context";
import { setLocaleCookie } from "@/lib/locale-cookie";
import { useAppTheme } from "@/lib/theme-context";
import { DashboardNav } from "@/components/dashboard/dashboard-nav";

const NotificationBell = dynamic(
  () => import("@/components/ui/NotificationBell").then((m) => m.NotificationBell),
  { ssr: false, loading: () => <span className="inline-block h-9 w-9" aria-hidden /> }
);

export function DashboardShell({ children }: { children: React.ReactNode }) {
  const tc = useTranslations("Common");
  const ta = useTranslations("App");
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const accessToken = useAuthStore((s) => s.accessToken);
  const setAuth = useAuthStore((s) => s.setAuth);
  const logout = useAuthStore((s) => s.logout);
  const { locale, setLocale } = useAppLocale();
  const { setTheme } = useAppTheme();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [languageOpen, setLanguageOpen] = useState(false);

  useEffect(() => {
    if (accessToken) return;
    let cancelled = false;
    (async () => {
      try {
        const r = await api.post<{ success: boolean; data?: { accessToken: string; expiresIn: number } }>("auth/refresh");
        const token = r.data?.data?.accessToken ?? null;
        if (!cancelled && token) {
          useAuthStore.getState().updateToken(token);
        }
      } catch {
        // ignore: user will be redirected by later auth checks
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [accessToken]);

  useEffect(() => {
    if (!accessToken) return;
    if (user) return;
    let cancelled = false;
    (async () => {
      try {
        const [meResult, prefsResult] = await Promise.allSettled([
          get<UserInfo>("auth/me"),
          get<{ theme: "system" | "light" | "dark" }>("auth/me/preferences"),
        ]);

        if (cancelled) return;

        if (meResult.status === "rejected") {
          logout();
          router.replace("/login");
          return;
        }

        const me = meResult.value;
        setAuth(me, accessToken);
        const rawLang = me.langue ?? null;
        const l = rawLang === "pt_pt" ? "pt-PT" : rawLang;
        if (l === "fr" || l === "en" || l === "pt-PT") {
          setLocaleCookie(l);
          setLocale(l);
        }

        if (prefsResult.status === "fulfilled" && prefsResult.value?.theme) {
          setTheme(prefsResult.value.theme);
        }
      } catch {
        if (!cancelled) {
          logout();
          router.replace("/login");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [accessToken, user, setAuth, logout, router, setTheme, setLocale]);

  async function handleLogout() {
    try {
      await api.post("auth/logout");
    } catch {
      /* ignore */
    }
    logout();
    router.replace("/login");
  }

  function changeLocale(next: AppLocale) {
    setLocaleCookie(next);
    setLocale(next);
  }

  function localeLabel(l: AppLocale) {
    if (l === "fr") return tc("localeFr");
    if (l === "en") return tc("localeEn");
    return tc("localePt");
  }

  const LanguagePicker = ({ compact }: { compact?: boolean }) => (
    <div className={compact ? "" : "relative"}>
      <Button
        type="button"
        variant="ghost"
        size={compact ? "default" : "sm"}
        className="mb-2 w-full justify-start gap-2"
        onClick={() => setLanguageOpen((v) => !v)}
      >
        <Languages className="h-4 w-4" />
        {localeLabel(locale)}
      </Button>

      {languageOpen ? (
        <div className="mb-2 flex flex-col gap-1">
          {(["fr", "en", "pt-PT"] as AppLocale[]).map((l) => (
            <Button
              key={l}
              type="button"
              variant={locale === l ? "secondary" : "ghost"}
              size={compact ? "default" : "sm"}
              className="w-full justify-start"
              onClick={() => {
                changeLocale(l);
                setLanguageOpen(false);
              }}
            >
              {localeLabel(l)}
            </Button>
          ))}
        </div>
      ) : null}
    </div>
  );

  return (
    <div className="flex min-h-screen bg-background">
      <aside className="hidden w-64 flex-col border-r border-border bg-card p-4 text-card-foreground md:flex">
        <div className="mb-8 flex items-center gap-2 text-foreground">
          <Building2 className="h-8 w-8 text-indigo-600" />
          <div>
            <p className="text-sm font-semibold leading-tight">{ta("name")}</p>
            <p className="text-xs text-muted-foreground">{ta("tagline")}</p>
          </div>
        </div>
        <nav className="flex flex-1 flex-col gap-0.5">
          <DashboardNav />
        </nav>
        <div className="mt-auto border-t border-border pt-4">
          <LanguagePicker />
          <p className="truncate text-xs text-muted-foreground">{user?.email}</p>
          <p className="truncate text-xs font-medium text-foreground">{user?.organisationNom}</p>
          <Button variant="outline" className="mt-3 w-full justify-start gap-2" type="button" onClick={handleLogout}>
            <LogOut className="h-4 w-4" />
            {tc("logout")}
          </Button>
        </div>
      </aside>

      {mobileOpen ? (
        <div className="fixed inset-0 z-50 md:hidden" role="dialog" aria-modal="true">
          <button
            type="button"
            className="absolute inset-0 bg-black/40"
            aria-label={tc("close")}
            onClick={() => setMobileOpen(false)}
          />
          <div className="absolute left-0 top-0 flex h-full w-[min(100%,20rem)] flex-col border-r border-border bg-card p-4 text-card-foreground shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <span className="font-semibold text-foreground">{tc("menu")}</span>
              <Button type="button" variant="ghost" size="icon" onClick={() => setMobileOpen(false)} aria-label={tc("close")}>
                <X className="h-5 w-5" />
              </Button>
            </div>
            <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto">
              <DashboardNav onNavigate={() => setMobileOpen(false)} />
            </nav>
            <div className="mt-auto border-t border-border pt-4">
              <LanguagePicker compact />
              <Button variant="outline" className="w-full justify-start gap-2" type="button" onClick={handleLogout}>
                <LogOut className="h-4 w-4" />
                {tc("logout")}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-border bg-card px-4 py-3 text-card-foreground md:hidden">
          <div className="flex min-w-0 items-center gap-2">
            <Button type="button" variant="ghost" size="icon" onClick={() => setMobileOpen(true)} aria-label={tc("menu")}>
              <Menu className="h-6 w-6 text-indigo-600" />
            </Button>
            <span className="truncate font-semibold">{ta("name")}</span>
          </div>
          <div className="flex items-center gap-2">
            <NotificationBell />
            <Button size="sm" variant="outline" onClick={handleLogout}>
              {tc("exit")}
            </Button>
          </div>
        </header>
        <main className="flex-1 p-4 md:p-6">{children}</main>
      </div>
    </div>
  );
}
