"use client";

import { useAuthStore, type UserInfo } from "@/lib/store";
import { decodeJwtPayload } from "@/lib/jwt";
import { api, get } from "@/lib/api";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Building2, LogOut, Menu, X, Languages, Settings } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTranslations } from "next-intl";
import { useAppLocale } from "@/lib/locale-context";
import type { AppLocale } from "@/lib/locale-context";
import { setLocaleCookie } from "@/lib/locale-cookie";

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
  const router = useRouter();
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);
  const accessToken = useAuthStore((s) => s.accessToken);
  const setAuth = useAuthStore((s) => s.setAuth);
  const logout = useAuthStore((s) => s.logout);
  const { locale, setLocale } = useAppLocale();
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
  }, [accessToken, user, setAuth, logout, router]);

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
    { href: "/rh/conges", label: tRhCong("title") },
    ...(showMyPaie ? [{ href: "/rh/me/paie", label: t("myPaie") }] : []),
    ...(showPaie ? [{ href: "/rh/paie", label: tRhPaie("title") }] : []),
  ];

  const restItems: NavItem[] = [
    ...(showBudget ? [{ href: "/budget", label: t("budget") }] : []),
    ...(showInv ? [{ href: "/inventaire", label: t("inventaire") }] : []),
    ...(showRapports ? [{ href: "/rapports", label: t("rapports") }] : []),
    ...(isAdmin
      ? [
          { href: "/dashboard/admin/users", label: t("adminUsers") },
          { href: "/dashboard/admin/audit", label: t("adminAudit") },
        ]
      : []),
  ];

  const linkClass = (href: string) =>
    `rounded-md px-3 py-2 text-sm font-medium ${
      pathname === href || pathname.startsWith(href + "/") ? "bg-indigo-50 text-indigo-800" : "text-slate-700 hover:bg-slate-100"
    }`;

  const NavLinks = ({ onNavigate }: { onNavigate?: () => void }) => (
    <>
      {!isEmploye ? (
        <Link href="/dashboard" onClick={onNavigate} className={linkClass("/dashboard")}>
          {t("dashboard")}
        </Link>
      ) : null}

      {financeItems.length > 0 ? (
        <>
          <p className="px-3 pt-3 text-xs font-semibold uppercase tracking-wide text-slate-400">{t("sectionFinance")}</p>
          {financeItems.map((item) => (
            <Link key={item.href} href={item.href} onClick={onNavigate} className={linkClass(item.href)}>
              {item.label}
            </Link>
          ))}
        </>
      ) : null}

      {rhItems.length > 0 ? (
        <>
          <p className="px-3 pt-3 text-xs font-semibold uppercase tracking-wide text-slate-400">{t("sectionRh")}</p>
          {rhItems.map((item) => (
            <Link key={item.href} href={item.href} onClick={onNavigate} className={linkClass(item.href)}>
              {item.label}
            </Link>
          ))}
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
          pathname.startsWith("/dashboard/settings") ? "bg-indigo-50 text-indigo-800" : "text-slate-700 hover:bg-slate-100"
        }`}
      >
        <Settings className="h-4 w-4 shrink-0 opacity-70" />
        {t("settings")}
      </Link>
    </>
  );

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside className="hidden w-64 flex-col border-r border-slate-200 bg-white p-4 md:flex">
        <div className="mb-8 flex items-center gap-2 text-slate-900">
          <Building2 className="h-8 w-8 text-indigo-600" />
          <div>
            <p className="text-sm font-semibold leading-tight">{ta("name")}</p>
            <p className="text-xs text-slate-500">{ta("tagline")}</p>
          </div>
        </div>
        <nav className="flex flex-1 flex-col gap-0.5">
          <NavLinks />
        </nav>
        <div className="mt-auto border-t border-slate-100 pt-4">
          <LanguagePicker />
          <p className="truncate text-xs text-slate-500">{user?.email}</p>
          <p className="truncate text-xs font-medium text-slate-800">{user?.organisationNom}</p>
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
            className="absolute inset-0 bg-slate-900/40"
            aria-label={tc("close")}
            onClick={() => setMobileOpen(false)}
          />
          <div className="absolute left-0 top-0 flex h-full w-[min(100%,20rem)] flex-col border-r border-slate-200 bg-white p-4 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <span className="font-semibold text-slate-900">{tc("menu")}</span>
              <Button type="button" variant="ghost" size="icon" onClick={() => setMobileOpen(false)} aria-label={tc("close")}>
                <X className="h-5 w-5" />
              </Button>
            </div>
            <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto">
              <NavLinks onNavigate={() => setMobileOpen(false)} />
            </nav>
            <div className="mt-auto border-t border-slate-100 pt-4">
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
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 md:hidden">
          <div className="flex min-w-0 items-center gap-2">
            <Button type="button" variant="ghost" size="icon" onClick={() => setMobileOpen(true)} aria-label={tc("menu")}>
              <Menu className="h-6 w-6 text-indigo-600" />
            </Button>
            <span className="truncate font-semibold">{ta("name")}</span>
          </div>
          <Button size="sm" variant="outline" onClick={handleLogout}>
            {tc("exit")}
          </Button>
        </header>
        <main className="flex-1 p-4 md:p-6">{children}</main>
      </div>
    </div>
  );
}
