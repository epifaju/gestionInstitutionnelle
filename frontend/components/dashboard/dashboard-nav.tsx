"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle, FileText, Settings } from "lucide-react";

import { useAuthStore } from "@/lib/store";
import { getEcheancesDashboard } from "@/services/contrat.service";
import { todoDashboardService } from "@/services/todo-dashboard.service";

type NavItem = { href: string; label: string };

function UrgentBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-semibold text-white">
      {count > 99 ? "99+" : count.toString()}
    </span>
  );
}

function linkClass(pathname: string, href: string) {
  return `rounded-md px-3 py-2 text-sm font-medium ${
    pathname === href || pathname.startsWith(href + "/")
      ? "bg-indigo-50 text-indigo-800"
      : "text-muted-foreground hover:bg-muted hover:text-foreground"
  }`;
}

/** Nav isolée : les refetch badges ne re-rendent pas le shell / le contenu principal. */
export function DashboardNav({ onNavigate }: { onNavigate?: () => void }) {
  const t = useTranslations("Nav");
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);
  const accessToken = useAuthStore((s) => s.accessToken);

  const role = user?.role;
  const isAdmin = role === "ADMIN";
  const isFin = role === "FINANCIER";
  const isRh = role === "RH";
  const isLog = role === "LOGISTIQUE";
  const isEmploye = role === "EMPLOYE";

  const showFinance = isAdmin || isFin;
  const showRhSalaries = isAdmin || isRh || isFin;
  const showRhContrats = isAdmin || isRh;
  const showPaie = isAdmin || isRh || isFin;
  const showMyPaie = isEmploye;
  const showBudget = isAdmin || isFin || isRh;
  const showInv = isAdmin || isLog || isFin;
  const showRapports = isAdmin || isFin || isRh || isLog;

  const financeItems: NavItem[] = showFinance
    ? [
        { href: "/finance/factures", label: t("factures") },
        { href: "/finance/paiements", label: t("paiements") },
        { href: "/finance/recettes", label: t("recettes") },
        { href: "/finance/taux-change", label: t("tauxChange") },
        ...(isAdmin ? [{ href: "/finance/categories", label: t("categories") }] : []),
      ]
    : [];

  const rhItems: NavItem[] = [
    ...(showRhSalaries ? [{ href: "/rh/salaries", label: t("salaries") }] : []),
    ...(showRhContrats ? [{ href: "/rh/contrats", label: t("rhContrats") }] : []),
    { href: "/rh/conges", label: t("conges") },
    ...(showMyPaie ? [{ href: "/rh/me/paie", label: t("myPaie") }] : []),
    ...(showPaie ? [{ href: "/rh/paie", label: t("paie") }] : []),
    ...(isAdmin || isRh ? [{ href: "/rh/paie/parametres", label: t("paieSettings") }] : []),
  ];

  const restItems: NavItem[] = [
    { href: "/notifications", label: t("notifications") },
    { href: "/documents", label: t("documents") },
    { href: "/courriers", label: t("courriers") },
    { href: "/missions", label: t("missions") },
    ...(showBudget ? [{ href: "/budget", label: t("budget") }] : []),
    ...(showInv ? [{ href: "/inventaire", label: t("inventaire") }] : []),
    ...(showRapports
      ? [
          { href: "/rapports", label: t("rapports") },
          { href: "/rapports/conformite", label: t("rapportsConformite") },
        ]
      : []),
    ...(isAdmin
      ? [
          { href: "/dashboard/admin/organisation", label: t("adminOrganisation") },
          { href: "/dashboard/admin/users", label: t("adminUsers") },
          { href: "/dashboard/admin/workflows", label: t("adminWorkflows") },
          { href: "/dashboard/admin/templates", label: t("adminTemplates") },
          { href: "/admin/audit", label: t("adminAudit") },
        ]
      : []),
  ];

  const { data: rhDash } = useQuery({
    queryKey: ["rh", "contrats", "dashboard", "nav-badge"],
    queryFn: () => getEcheancesDashboard(),
    enabled: !!accessToken && showRhContrats,
    staleTime: 60_000,
  });

  const { data: comptages } = useQuery({
    queryKey: ["sidebar-comptages"],
    queryFn: () => todoDashboardService.getComptages(),
    staleTime: 5 * 60 * 1000,
    refetchInterval: 5 * 60 * 1000,
    enabled: !!accessToken && user?.role !== "EMPLOYE",
  });

  const totalUrgentTodo = useMemo(
    () => Object.values(comptages ?? {}).reduce((a, b) => a + Number(b ?? 0), 0),
    [comptages]
  );

  const rhUrgentBadge = useMemo(() => {
    if (!rhDash) return 0;
    return Number(rhDash.critiques ?? 0) + Number(rhDash.urgentes ?? 0);
  }, [rhDash]);

  return (
    <>
      {!isEmploye ? (
        <Link
          href="/dashboard"
          prefetch
          onClick={onNavigate}
          className={`${linkClass(pathname, "/dashboard")} flex items-center justify-between gap-2`}
        >
          <span>{t("dashboard")}</span>
          <UrgentBadge count={totalUrgentTodo} />
        </Link>
      ) : null}

      {!isEmploye ? (
        <Link
          href="/todo"
          prefetch
          onClick={onNavigate}
          className={`${linkClass(pathname, "/todo")} flex items-center justify-between gap-2`}
        >
          <span className="inline-flex items-center gap-2">
            <CheckCircle className="h-4 w-4 opacity-70" />
            {t("todo")}
          </span>
          <UrgentBadge count={totalUrgentTodo} />
        </Link>
      ) : null}

      {financeItems.length > 0 ? (
        <>
          <p className="px-3 pt-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("sectionFinance")}</p>
          {financeItems.map((item) => (
            <Link key={item.href} href={item.href} prefetch onClick={onNavigate} className={linkClass(pathname, item.href)}>
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
                prefetch
                onClick={onNavigate}
                className={`${linkClass(pathname, item.href)} flex items-center justify-between gap-2`}
              >
                <span className="inline-flex items-center gap-2">
                  <FileText className="h-4 w-4 opacity-70" />
                  {item.label}
                </span>
                <UrgentBadge count={rhUrgentBadge} />
              </Link>
            ) : (
              <Link key={item.href} href={item.href} prefetch onClick={onNavigate} className={linkClass(pathname, item.href)}>
                {item.label}
              </Link>
            )
          )}
        </>
      ) : null}

      {restItems.map((item) => (
        <Link key={item.href} href={item.href} prefetch onClick={onNavigate} className={linkClass(pathname, item.href)}>
          {item.label}
        </Link>
      ))}

      <Link
        href="/dashboard/settings"
        prefetch
        onClick={onNavigate}
        className={`mt-1 flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium ${
          pathname.startsWith("/dashboard/settings")
            ? "bg-indigo-50 text-indigo-800"
            : "text-muted-foreground hover:bg-muted hover:text-foreground"
        }`}
      >
        <Settings className="h-4 w-4 shrink-0 opacity-70" />
        {t("settings")}
      </Link>
    </>
  );
}
