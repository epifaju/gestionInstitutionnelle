"use client";

import { useAuthStore, type UserInfo } from "@/lib/store";
import { decodeJwtPayload } from "@/lib/jwt";
import { api, get } from "@/lib/api";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Building2, FileText, LogOut, Menu, X, Languages, Settings } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTranslations } from "next-intl";
import { useAppLocale } from "@/lib/locale-context";
import type { AppLocale } from "@/lib/locale-context";
import { setLocaleCookie } from "@/lib/locale-cookie";
import { NotificationBell } from "@/components/ui/NotificationBell";
import { useAppTheme } from "@/lib/theme-context";
import { useQuery } from "@tanstack/react-query";
import { getEcheancesDashboard } from "@/services/contrat.service";

function syncTokenFromCookie() {
  if (typeof document === "undefined") return;
  const m = document.cookie.match(/(?:^|; )access_token=([^;]*)/);
  if (!m?.[1]) return;
  const raw = decodeURIComponent(m[1]);
  if (!raw) return;
  const current = useAuthStore.getState().accessToken;
  if (current === raw) return;
  const payload = decodeJwtPayload(raw);
  const exp = payload?.exp;
  const maxAge = exp ? Math.max(0, exp - Math.floor(Date.now() / 1000)) : 900;
  useAuthStore.getState().updateToken(raw, maxAge);
}

type NavItem = { href: string; label: string };

export function DashboardShell({ children }: { children: React.ReactNode }) {
  const t = useTranslations("Nav");
  const tc = useTranslations("Common");
  const ta = useTranslations("App");
  const tf = useTranslations("Finance.factures");
  const tp = useTranslations("Finance.paiements");
  const tr = useTranslations("Finance.recettes");
  const tt = useTranslations("Finance.tauxChange");
  const tCat = useTranslations("Finance.categories");
  const tRhSal = useTranslations("RH.salaries");
  const tRhCong = useTranslations("RH.conges");
  const tRhPaie = useTranslations("RH.paie");
  const tRhPaieSettings = useTranslations("RH.paieSettings");
  const router = useRouter();
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);
  const accessToken = useAuthStore((s) => s.accessToken);
  const setAuth = useAuthStore((s) => s.setAuth);
  const logout = useAuthStore((s) => s.logout);
  const { locale, setLocale } = useAppLocale();
  const { setTheme } = useAppTheme();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [languageOpen, setLanguageOpen] = useState(false);

  useEffect(() => {
    syncTokenFromCookie();
  }, []);

  useEffect(() => {
    if (!accessToken) return;
    if (user) return;
    let cancelled = false;
    (async () => {
      try {
        const me = await get<UserInfo>("auth/me");
        if (!cancelled) {
          setAuth(me, accessToken, 900);
          // IMPORTANT: la langue est un paramètre global (cookie/localStorage),
          // donc on la resynchronise explicitement à chaque login en fonction de l'utilisateur.
          const rawLang = me.langue ?? null;
          const l = rawLang === "pt_pt" ? "pt-PT" : rawLang;
          if (l === "fr" || l === "en" || l === "pt-PT") {
            setLocaleCookie(l);
            setLocale(l);
          }
          try {
            const prefs = await get<{ theme: "system" | "light" | "dark" }>("auth/me/preferences");
            if (!cancelled && prefs?.theme) setTheme(prefs.theme);
          } catch {
            /* ignore: keep current theme */
          }
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
        <div className={compact ? "mb-2 flex flex-col gap-1" : "mb-2 flex flex-col gap-1"}>
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

  const role = user?.role;
  const isAdmin = role === "ADMIN";
  const isFin = role === "FINANCIER";
  const isRh = role === "RH";
  const isLog = role === "LOGISTIQUE";
  const isEmploye = role === "EMPLOYE";

  const showFinance = isAdmin || isFin;
  const showRhSalaries = isAdmin || isRh;
  const showRhContrats = isAdmin || isRh;
  const showPaie = isAdmin || isRh || isFin;
  const showMyPaie = isEmploye;
  const showBudget = isAdmin || isFin || isRh;
  const showInv = isAdmin || isLog;
  const showRapports = isAdmin || isFin || isRh || isLog;

  const financeItems: NavItem[] = showFinance
    ? [
        { href: "/finance/factures", label: tf("title") },
        { href: "/finance/paiements", label: tp("title") },
        { href: "/finance/recettes", label: tr("title") },
        { href: "/finance/taux-change", label: tt("title") },
        ...(isAdmin ? [{ href: "/finance/categories", label: tCat("title") }] : []),
      ]
    : [];

  const rhItems: NavItem[] = [
    ...(showRhSalaries ? [{ href: "/rh/salaries", label: tRhSal("title") }] : []),
    ...(showRhContrats ? [{ href: "/rh/contrats", label: t("rhContrats") }] : []),
    { href: "/rh/conges", label: tRhCong("title") },
    ...(showMyPaie ? [{ href: "/rh/me/paie", label: t("myPaie") }] : []),
    ...(showPaie ? [{ href: "/rh/paie", label: tRhPaie("title") }] : []),
    ...(isAdmin || isRh ? [{ href: "/rh/paie/parametres", label: tRhPaieSettings("title") }] : []),
  ];

  const restItems: NavItem[] = [
    { href: "/notifications", label: t("notifications") },
    { href: "/documents", label: t("documents") },
    { href: "/courriers", label: t("courriers") },
    { href: "/missions", label: t("missions") },
    ...(showBudget ? [{ href: "/budget", label: t("budget") }] : []),
    ...(showInv ? [{ href: "/inventaire", label: t("inventaire") }] : []),
    ...(showRapports ? [{ href: "/rapports", label: t("rapports") }] : []),
    ...(isAdmin
      ? [
          { href: "/dashboard/admin/users", label: t("adminUsers") },
          { href: "/dashboard/admin/workflows", label: t("adminWorkflows") },
          { href: "/dashboard/admin/templates", label: t("adminTemplates") },
          { href: "/dashboard/admin/audit", label: t("adminAudit") },
        ]
      : []),
  ];

  const linkClass = (href: string) =>
    `rounded-md px-3 py-2 text-sm font-medium ${
      pathname === href || pathname.startsWith(href + "/") ? "bg-indigo-50 text-indigo-800" : "text-muted-foreground hover:bg-muted hover:text-foreground"
    }`;

  const { data: rhDash } = useQuery({
    queryKey: ["rh", "contrats", "dashboard", "nav-badge"],
    queryFn: () => getEcheancesDashboard(),
    enabled: !!accessToken && showRhContrats,
    staleTime: 60_000,
  });

  const rhUrgentBadge = useMemo(() => {
    if (!rhDash) return 0;
    return Number(rhDash.critiques ?? 0) + Number(rhDash.urgentes ?? 0);
  }, [rhDash]);

  const NavLinks = ({ onNavigate }: { onNavigate?: () => void }) => (
    <>
      {!isEmploye ? (
        <Link href="/dashboard" onClick={onNavigate} className={linkClass("/dashboard")}>
          {t("dashboard")}
        </Link>
      ) : null}

      {financeItems.length > 0 ? (
        <>
          <p className="px-3 pt-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("sectionFinance")}</p>
          {financeItems.map((item) => (
            <Link key={item.href} href={item.href} onClick={onNavigate} className={linkClass(item.href)}>
              {item.label}
            </Link>
          ))}
        </>
      ) : null}

      {rhItems.length > 0 ? (
        <>
          <p className="px-3 pt-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("sectionRh")}</p>
          {rhItems.map((item) =>
            item.href === "/rh/contrats" ? (
              <Link
                key={item.href}
                href={item.href}
                onClick={onNavigate}
                className={`${linkClass(item.href)} flex items-center justify-between gap-2`}
              >
                <span className="inline-flex items-center gap-2">
                  <FileText className="h-4 w-4 opacity-70" />
                  {item.label}
                </span>
                {rhUrgentBadge > 0 ? (
                  <span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-semibold text-white">{rhUrgentBadge}</span>
                ) : null}
              </Link>
            ) : (
              <Link key={item.href} href={item.href} onClick={onNavigate} className={linkClass(item.href)}>
                {item.label}
              </Link>
            )
          )}
        </>
      ) : null}

      {restItems.map((item) => (
        <Link key={item.href} href={item.href} onClick={onNavigate} className={linkClass(item.href)}>
          {item.label}
        </Link>
      ))}

      <Link
        href="/dashboard/settings"
        onClick={onNavigate}
        className={`mt-1 flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium ${
          pathname.startsWith("/dashboard/settings") ? "bg-indigo-50 text-indigo-800" : "text-muted-foreground hover:bg-muted hover:text-foreground"
        }`}
      >
        <Settings className="h-4 w-4 shrink-0 opacity-70" />
        {t("settings")}
      </Link>
    </>
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
          <NavLinks />
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
              <NavLinks onNavigate={() => setMobileOpen(false)} />
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
