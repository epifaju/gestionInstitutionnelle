"use client";

import type { ComponentType } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import {
  ArrowDownRight,
  ArrowUpRight,
  Briefcase,
  Coins,
  PiggyBank,
  TrendingDown,
  Users,
} from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuthStore } from "@/lib/store";
import { cn } from "@/lib/utils";
import { getDashboard } from "@/services/dashboard.service";
import { getEcheancesDashboard } from "@/services/contrat.service";
import { tauxDuJour } from "@/services/devises.service";
import { getExpiringSoon } from "@/services/documents.service";
import Link from "next/link";

function num(s: string | number | undefined) {
  if (s === undefined || s === null) return 0;
  const n = typeof s === "number" ? s : parseFloat(String(s));
  return Number.isFinite(n) ? n : 0;
}

function fmtEur(n: number) {
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(n);
}

function pctDelta(cur: number, prev: number) {
  if (prev === 0) return cur === 0 ? 0 : 100;
  return ((cur - prev) / Math.abs(prev)) * 100;
}

function KpiCard({
  title,
  value,
  deltaPct,
  invertDelta,
  icon: Icon,
  accent,
  deltaLabel,
}: {
  title: string;
  value: string;
  deltaPct: number | null;
  invertDelta?: boolean;
  icon: ComponentType<{ className?: string }>;
  accent: "rose" | "emerald" | "sky" | "violet";
  deltaLabel: string;
}) {
  const ring =
    accent === "rose"
      ? "border-rose-100 bg-rose-50/80 dark:border-rose-950/30 dark:bg-rose-950/25"
      : accent === "emerald"
        ? "border-emerald-100 bg-emerald-50/80 dark:border-emerald-950/30 dark:bg-emerald-950/25"
        : accent === "sky"
          ? "border-sky-100 bg-sky-50/80 dark:border-sky-950/30 dark:bg-sky-950/25"
          : "border-violet-100 bg-violet-50/80 dark:border-violet-950/30 dark:bg-violet-950/25";
  const iconBg =
    accent === "rose"
      ? "bg-rose-100 text-rose-700 dark:bg-rose-950/40 dark:text-rose-200"
      : accent === "emerald"
        ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200"
        : accent === "sky"
          ? "bg-sky-100 text-sky-700 dark:bg-sky-950/40 dark:text-sky-200"
          : "bg-violet-100 text-violet-700 dark:bg-violet-950/40 dark:text-violet-200";
  const upBad = invertDelta ?? false;
  const good = deltaPct === null ? null : upBad ? deltaPct <= 0 : deltaPct >= 0;
  return (
    <Card className={`border ${ring}`}>
      <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
        <div className={`rounded-lg p-2 ${iconBg}`}>
          <Icon className="h-4 w-4" />
        </div>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-semibold tracking-tight text-foreground">{value}</div>
        {deltaPct !== null && (
          <p
            className={`mt-1 flex items-center gap-1 text-xs font-medium ${
              good ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400"
            }`}
          >
            {good ? <ArrowDownRight className="h-3.5 w-3.5" /> : <ArrowUpRight className="h-3.5 w-3.5" />}
            {deltaPct >= 0 ? "+" : ""}
            {deltaPct.toFixed(1)} % {deltaLabel}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const td = useTranslations("Dashboard");
  const user = useAuthStore((s) => s.user);
  const { data, isLoading, error } = useQuery({ queryKey: ["rapports", "dashboard"], queryFn: getDashboard });
  const isEmploye = user?.role === "EMPLOYE";
  const canSeeDocs = user?.role === "RH" || user?.role === "ADMIN";
  const canSeeRhUrgent = user?.role === "RH" || user?.role === "ADMIN";
  const { data: fx } = useQuery({
    queryKey: ["devises", "taux-du-jour", "EUR"],
    queryFn: () => tauxDuJour("EUR"),
    staleTime: 1000 * 60 * 30,
    enabled: !isEmploye,
  });

  const { data: expDocs } = useQuery({
    queryKey: ["documents", "expiringSoon", 30],
    queryFn: () => getExpiringSoon(30),
    enabled: canSeeDocs && !isEmploye,
    staleTime: 1000 * 60 * 30,
  });

  const { data: rhDash } = useQuery({
    queryKey: ["rh", "contrats", "dashboard", "home-widget"],
    queryFn: () => getEcheancesDashboard(),
    enabled: canSeeRhUrgent && !isEmploye,
    staleTime: 60_000,
  });

  if (error) {
    return <p className="text-sm text-red-600">{td("loadError")}</p>;
  }

  if (isLoading || !data) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <div className="grid gap-4 sm:grid-cols-2">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
        <Skeleton className="h-72" />
      </div>
    );
  }

  if (isEmploye) {
    return (
      <div className="space-y-8">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{td("title")}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{td("employeeSubtitle")}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            {data.moisCourant.mois.toString().padStart(2, "0")}/{data.moisCourant.annee}
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Briefcase className="h-4 w-4" />
              {td("employeeCongesOnly")}
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {data.congesEnCours.length === 0 ? (
              <p className="text-sm text-muted-foreground">{td("congesEmpty")}</p>
            ) : (
              data.congesEnCours.map((c) => (
                <Badge key={`${c.salarieNomComplet}-${c.dateDebut}`} variant="secondary" className="text-xs">
                  {c.salarieNomComplet}{" "}
                  <span className="text-muted-foreground">
                    ({c.dateDebut} → {c.dateFin})
                  </span>
                </Badge>
              ))
            )}
          </CardContent>
        </Card>

        <p className="text-xs text-muted-foreground">
          {td("footerConges")} : {data.kpis.congesEnCours}
        </p>
      </div>
    );
  }

  const ev = data.evolution6Mois;
  const cur = ev.length >= 1 ? ev[ev.length - 1] : null;
  const prev = ev.length >= 2 ? ev[ev.length - 2] : null;
  const dCur = cur ? num(cur.depenses) : 0;
  const dPrev = prev ? num(prev.depenses) : 0;
  const rCur = cur ? num(cur.recettes) : 0;
  const rPrev = prev ? num(prev.recettes) : 0;
  const sCur = rCur - dCur;
  const sPrev = rPrev - dPrev;

  const chartData = ev.map((m) => ({
    mois: m.mois,
    Dépenses: num(m.depenses),
    Recettes: num(m.recettes),
  }));

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{td("title")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {td("subtitle")} — {data.moisCourant.mois.toString().padStart(2, "0")}/{data.moisCourant.annee}
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          title={td("kpiDepenses")}
          value={fmtEur(num(data.kpis.totalDepenses))}
          deltaPct={pctDelta(dCur, dPrev)}
          invertDelta
          icon={TrendingDown}
          accent="rose"
          deltaLabel={td("deltaVsPrev")}
        />
        <KpiCard
          title={td("kpiRecettes")}
          value={fmtEur(num(data.kpis.totalRecettes))}
          deltaPct={pctDelta(rCur, rPrev)}
          icon={Coins}
          accent="emerald"
          deltaLabel={td("deltaVsPrev")}
        />
        <KpiCard
          title={td("kpiSolde")}
          value={fmtEur(num(data.kpis.solde))}
          deltaPct={pctDelta(sCur, sPrev)}
          icon={PiggyBank}
          accent="sky"
          deltaLabel={td("deltaVsPrev")}
        />
        <KpiCard
          title={td("kpiEffectifs")}
          value={String(data.kpis.effectifsActifs)}
          deltaPct={null}
          icon={Users}
          accent="violet"
          deltaLabel={td("deltaVsPrev")}
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{td("chartTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="h-72">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
              <XAxis dataKey="mois" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip formatter={(v) => fmtEur(typeof v === "number" ? v : Number(v))} />
              <Legend />
              <Bar dataKey="Dépenses" fill="#f43f5e" radius={[4, 4, 0, 0]} />
              <Bar dataKey="Recettes" fill="#10b981" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{td("alertsTitle")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {data.alertesBudget.length === 0 ? (
              <p className="text-sm text-muted-foreground">{td("alertsEmpty")}</p>
            ) : (
              data.alertesBudget.map((a) => (
                <div key={a.categorie} className="space-y-1">
                  <div className="flex justify-between text-sm">
                    <span className="font-medium text-foreground">{a.categorie}</span>
                    <span className="text-rose-600 dark:text-rose-400">{num(a.tauxExecution).toFixed(1)} %</span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-rose-500 transition-all"
                      style={{ width: `${Math.min(num(a.tauxExecution), 100)}%` }}
                    />
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Coins className="h-4 w-4" />
              Taux du jour
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Devise</TableHead>
                  <TableHead className="text-right">Vers EUR</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {["USD", "GBP", "CHF", "XOF"].map((c) => (
                  <TableRow key={c}>
                    <TableCell className="font-medium">{c}</TableCell>
                    <TableCell className="text-right tabular-nums">{fx ? Number(fx[c] ?? 0).toFixed(6) : "—"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <p className="mt-2 text-xs text-muted-foreground">Source: Frankfurter • Cache 4h</p>
          </CardContent>
        </Card>

        {canSeeDocs ? (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Documents expirant bientôt</CardTitle>
            </CardHeader>
            <CardContent>
              {!expDocs || expDocs.length === 0 ? (
                <p className="text-sm text-muted-foreground">Aucun document expirant dans les 30 prochains jours.</p>
              ) : (
                <>
                  <p className="text-sm text-muted-foreground">
                    {expDocs.length} document(s) expirant dans les 30 prochains jours.
                  </p>
                  <div className="mt-3 space-y-2">
                    {expDocs.slice(0, 5).map((d) => (
                      <div key={d.id} className="flex items-center justify-between gap-3 text-sm">
                        <span className="truncate font-medium text-foreground">{d.titre}</span>
                        <span className="shrink-0 text-muted-foreground">{d.dateExpiration ?? "—"}</span>
                      </div>
                    ))}
                  </div>
                  <div className="mt-3">
                    <Link href="/documents?expirantBientot=true" className="text-sm font-medium text-indigo-700 hover:underline">
                      Voir tout
                    </Link>
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        ) : null}

        <Card>
          <CardHeader>
            <CardTitle className="text-base">{td("topSuppliers")}</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{td("supplier")}</TableHead>
                  <TableHead className="text-right">{td("amount")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.top5Fournisseurs.map((t) => (
                  <TableRow key={t.fournisseur}>
                    <TableCell>{t.fournisseur}</TableCell>
                    <TableCell className="text-right tabular-nums">{fmtEur(num(t.montant))}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Briefcase className="h-4 w-4" />
            {td("congesTitle")}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {data.congesEnCours.length === 0 ? (
            <p className="text-sm text-muted-foreground">{td("congesEmpty")}</p>
          ) : (
            data.congesEnCours.map((c) => (
              <Badge key={`${c.salarieNomComplet}-${c.dateDebut}`} variant="secondary" className="text-xs">
                {c.salarieNomComplet}{" "}
                <span className="text-muted-foreground">
                  ({c.dateDebut} → {c.dateFin})
                </span>
              </Badge>
            ))
          )}
        </CardContent>
      </Card>

      {canSeeRhUrgent && rhDash ? (
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-base">{td("rhUrgentTitle")}</CardTitle>
            <Link href="/rh/contrats" className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
              {td("rhUrgentSeeAll")}
            </Link>
          </CardHeader>
          <CardContent className="space-y-2">
            {(rhDash.prochainesEcheances ?? []).slice(0, 5).length === 0 ? (
              <p className="text-sm text-muted-foreground">{td("rhUrgentEmpty")}</p>
            ) : (
              <div className="space-y-2">
                {(rhDash.prochainesEcheances ?? []).slice(0, 5).map((e) => {
                  const n = (e.niveauUrgence ?? "NORMAL").toUpperCase();
                  const badgeCls =
                    n === "CRITIQUE"
                      ? "bg-red-900 text-white"
                      : n === "URGENT"
                        ? "bg-red-500 text-white"
                        : n === "ATTENTION"
                          ? "bg-orange-500 text-white"
                          : "bg-muted text-foreground";
                  return (
                    <div key={e.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border p-2 text-sm">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${badgeCls}`}>{n}</span>
                          <span className="font-medium text-foreground">{e.salarieNomComplet}</span>
                          <span className="text-muted-foreground">· {e.typeEcheance}</span>
                        </div>
                        <div className="text-xs text-muted-foreground">
                          {e.titre} · {e.dateEcheance}
                        </div>
                      </div>
                      <Link href="/rh/contrats" className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}>
                        {td("rhUrgentTreat")}
                      </Link>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      ) : null}

      <p className="text-xs text-muted-foreground">
        {td("footerParc")} : {fmtEur(num(data.kpis.valeurParcMateriel))} — {td("footerConges")} :{" "}
        {data.kpis.congesEnCours}
      </p>
    </div>
  );
}
