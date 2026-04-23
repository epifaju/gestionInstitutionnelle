"use client";

import axios from "axios";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
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
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuthStore } from "@/lib/store";
import { intlLocaleTag } from "@/lib/intl-locale";
import { creerBudget, getBudget, modifierLigneBudget, validerBudget } from "@/services/budget.service";
import { listCategories } from "@/services/finance.service";

function n(s: string | number) {
  const x = typeof s === "number" ? s : parseFloat(String(s));
  return Number.isFinite(x) ? x : 0;
}

function statutBadge(s: string): "success" | "muted" | "warning" {
  if (s === "VALIDE") return "success";
  if (s === "BROUILLON") return "muted";
  return "warning";
}

export default function BudgetPage() {
  const t = useTranslations("Budget");
  const tc = useTranslations("Common");
  const localeTag = intlLocaleTag(useLocale());
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === "ADMIN";
  const canEdit = user?.role === "ADMIN" || user?.role === "FINANCIER";

  const yearNow = new Date().getFullYear();
  const [annee, setAnnee] = useState(yearNow);
  const [tab, setTab] = useState<"DEPENSE" | "RECETTE">("DEPENSE");
  const [createOpen, setCreateOpen] = useState(false);
  const [createMode, setCreateMode] = useState<"create" | "revise">("create");
  const [montantsDep, setMontantsDep] = useState<Record<string, string>>({});
  const [montantsRec, setMontantsRec] = useState<Record<string, string>>({});
  const [notes, setNotes] = useState("");

  const { data: categories } = useQuery({ queryKey: ["finance", "categories"], queryFn: listCategories });
  const { data: budget, isLoading } = useQuery({
    queryKey: ["budget", annee],
    queryFn: () => getBudget(annee),
  });

  const depCats = useMemo(
    () => (categories ?? []).filter((c) => c.type === "DEPENSE"),
    [categories]
  );
  const recCats = useMemo(
    () => (categories ?? []).filter((c) => c.type === "RECETTE"),
    [categories]
  );

  const lignesTab = useMemo(() => {
    if (!budget) return [];
    return budget.lignes.filter((l) => l.type === tab);
  }, [budget, tab]);

  const chartData = useMemo(() => {
    return lignesTab.map((l) => ({
      cat: l.categorieLibelle,
      planned: n(l.montantPrevu),
      actual: n(l.montantRealise),
    }));
  }, [lignesTab]);

  const mutValider = useMutation({
    mutationFn: () => validerBudget(budget!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["budget", annee] });
    },
  });

  const mutLigne = useMutation({
    mutationFn: ({ ligneId, montant }: { ligneId: string; montant: string }) =>
      modifierLigneBudget(budget!.id, ligneId, montant),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["budget", annee] }),
  });

  const mutCreer = useMutation({
    mutationFn: () => {
      const lignes: { categorieId: string; type: string; montantPrevu: string }[] = [];
      for (const c of depCats) {
        const m = montantsDep[c.id];
        if (m && parseFloat(m) > 0) {
          lignes.push({ categorieId: c.id, type: "DEPENSE", montantPrevu: m });
        }
      }
      for (const c of recCats) {
        const m = montantsRec[c.id];
        if (m && parseFloat(m) > 0) {
          lignes.push({ categorieId: c.id, type: "RECETTE", montantPrevu: m });
        }
      }
      if (lignes.length === 0) throw new Error(t("createNoLines"));
      return creerBudget({ annee, lignes, notes: notes || null });
    },
    onSuccess: () => {
      setCreateOpen(false);
      setNotes("");
      setMontantsDep({});
      setMontantsRec({});
      qc.invalidateQueries({ queryKey: ["budget", annee] });
    },
  });

  function clampNonNegative(input: string) {
    const raw = input.replace(",", ".").trim();
    if (raw === "") return "";
    const val = Number(raw);
    if (!Number.isFinite(val)) return "";
    return String(Math.max(0, val));
  }

  const totalDepPrevu = useMemo(() => {
    let sum = 0;
    for (const v of Object.values(montantsDep)) {
      const x = Number(String(v).replace(",", "."));
      if (Number.isFinite(x) && x > 0) sum += x;
    }
    return sum;
  }, [montantsDep]);

  const totalRecPrevu = useMemo(() => {
    let sum = 0;
    for (const v of Object.values(montantsRec)) {
      const x = Number(String(v).replace(",", "."));
      if (Number.isFinite(x) && x > 0) sum += x;
    }
    return sum;
  }, [montantsRec]);

  const createErrorMessage = useMemo(() => {
    if (!mutCreer.isError) return null;
    const err = mutCreer.error;
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { code?: string; message?: string } | undefined;
      if (data?.code === "BUDGET_BROUILLON_EXISTE") {
        return t("errorDraftExists");
      }
    }
    return t("errorGeneric");
  }, [mutCreer.error, mutCreer.isError, t]);

  function ouvrirRevision() {
    if (!budget) return;
    const dep: Record<string, string> = {};
    const rec: Record<string, string> = {};
    for (const l of budget.lignes) {
      if (l.type === "DEPENSE") dep[l.categorieId] = String(l.montantPrevu ?? "");
      if (l.type === "RECETTE") rec[l.categorieId] = String(l.montantPrevu ?? "");
    }
    setMontantsDep(dep);
    setMontantsRec(rec);
    setNotes("");
    setCreateMode("revise");
    setCreateOpen(true);
  }

  function ouvrirCreation() {
    setMontantsDep({});
    setMontantsRec({});
    setNotes("");
    setCreateMode("create");
    setCreateOpen(true);
  }

  function fmtMoney(v: number) {
    return new Intl.NumberFormat(localeTag, { style: "currency", currency: "EUR" }).format(v);
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">{t("title")}</h1>
          <p className="mt-1 text-sm text-slate-600">{t("subtitle")}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Label className="sr-only" htmlFor="annee">
            {tc("year")}
          </Label>
          <select
            id="annee"
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            value={annee}
            onChange={(e) => setAnnee(parseInt(e.target.value, 10))}
          >
            {[yearNow - 1, yearNow, yearNow + 1].map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
        </div>
      </div>

      {isLoading && (
        <div className="space-y-4">
          <Skeleton className="h-10 w-full max-w-md" />
          <Skeleton className="h-64" />
        </div>
      )}

      {!isLoading && !budget && (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-slate-600">{t("empty")}</p>
            {canEdit && (
              <Button className="mt-4" onClick={ouvrirCreation}>
                {t("create")}
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      {budget && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <Badge variant={statutBadge(budget.statut)}>{budget.statut}</Badge>
            {isAdmin && budget.statut === "BROUILLON" && (
              <Button size="sm" onClick={() => mutValider.mutate()} disabled={mutValider.isPending}>
                {tc("validate")}
              </Button>
            )}
            {canEdit && budget.statut === "VALIDE" && (
              <Button size="sm" variant="outline" onClick={ouvrirRevision}>
                {t("revise")}
              </Button>
            )}
          </div>

          <div className="flex gap-2 border-b border-slate-200">
            <button
              type="button"
              className={`border-b-2 px-3 py-2 text-sm font-medium ${
                tab === "DEPENSE"
                  ? "border-rose-500 text-rose-700"
                  : "border-transparent text-slate-600 hover:text-slate-900"
              }`}
              onClick={() => setTab("DEPENSE")}
            >
              {t("tabDepenses")}
            </button>
            <button
              type="button"
              className={`border-b-2 px-3 py-2 text-sm font-medium ${
                tab === "RECETTE"
                  ? "border-emerald-500 text-emerald-700"
                  : "border-transparent text-slate-600 hover:text-slate-900"
              }`}
              onClick={() => setTab("RECETTE")}
            >
              {t("tabRecettes")}
            </button>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t("detailTitle")}</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("thCategorie")}</TableHead>
                    <TableHead className="text-right">{t("thPrevu")}</TableHead>
                    <TableHead className="text-right">{t("thRealise")}</TableHead>
                    <TableHead className="text-right">{t("thEcart")}</TableHead>
                    <TableHead className="text-right">{t("thExecutionPct")}</TableHead>
                    <TableHead className="w-40">{t("thProgression")}</TableHead>
                    {budget.statut === "BROUILLON" && canEdit && <TableHead />}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {lignesTab.map((l) => (
                    <TableRow key={l.id}>
                      <TableCell className="font-medium">{l.categorieLibelle}</TableCell>
                      <TableCell className="text-right tabular-nums">{fmtMoney(n(l.montantPrevu))}</TableCell>
                      <TableCell className="text-right tabular-nums">{fmtMoney(n(l.montantRealise))}</TableCell>
                      <TableCell className="text-right tabular-nums">{fmtMoney(n(l.ecart))}</TableCell>
                      <TableCell className="text-right tabular-nums">{n(l.tauxExecutionPct).toFixed(1)} %</TableCell>
                      <TableCell>
                        <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
                          <div
                            className={`h-full rounded-full ${l.alerteDepassement ? "bg-rose-500" : "bg-emerald-500"}`}
                            style={{ width: `${Math.min(n(l.tauxExecutionPct), 100)}%` }}
                          />
                        </div>
                      </TableCell>
                      {budget.statut === "BROUILLON" && canEdit && (
                        <TableCell>
                          <form
                            key={`${l.id}-${l.montantPrevu}`}
                            className="flex gap-1"
                            onSubmit={(e) => {
                              e.preventDefault();
                              const fd = new FormData(e.currentTarget);
                              const v = fd.get("montant") as string;
                              mutLigne.mutate({ ligneId: l.id, montant: v });
                            }}
                          >
                            <Input name="montant" defaultValue={l.montantPrevu} className="h-8 w-24 text-right" />
                            <Button type="submit" size="sm" variant="secondary" className="h-8">
                              {t("saveLine")}
                            </Button>
                          </form>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                {t("chartTitle", { scope: tab === "DEPENSE" ? t("scopeDepenses") : t("scopeRecettes") })}
              </CardTitle>
            </CardHeader>
            <CardContent className="h-80">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="cat" tick={{ fontSize: 10 }} interval={0} angle={-25} textAnchor="end" height={80} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(v) => fmtMoney(typeof v === "number" ? v : Number(v))} />
                  <Legend />
                  <Bar dataKey="planned" name={t("legendPrevu")} fill={tab === "DEPENSE" ? "#fda4af" : "#6ee7b7"} />
                  <Bar dataKey="actual" name={t("legendRealise")} fill={tab === "DEPENSE" ? "#f43f5e" : "#10b981"} />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </>
      )}

      {createOpen && categories && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg bg-white p-6 shadow-lg">
            <h2 className="text-lg font-semibold">
              {createMode === "revise" ? t("modalTitleRevise", { year: annee }) : t("modalTitleCreate", { year: annee })}
            </h2>
            <p className="mt-1 text-sm text-slate-600">
              {createMode === "revise" ? t("modalHintRevise") : t("modalHintCreate")}
            </p>
            <div className="mt-4 space-y-4">
              <div>
                <Label>{t("notesLabel")}</Label>
                <Input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t("optional")} />
              </div>
              <div>
                <div className="flex items-baseline justify-between gap-3">
                  <p className="text-sm font-medium text-rose-700">{t("tabDepenses")}</p>
                  <p className="text-xs text-slate-500">
                    {t("totalPrevu", { total: fmtMoney(totalDepPrevu) })}
                  </p>
                </div>
                <div className="mt-2 space-y-2">
                  {depCats.map((c) => (
                    <div key={c.id} className="flex items-center justify-between gap-2">
                      <span className="text-sm text-slate-700">{c.libelle}</span>
                      <Input
                        className="w-28 text-right"
                        type="number"
                        inputMode="decimal"
                        min={0}
                        step="0.01"
                        placeholder="0"
                        value={montantsDep[c.id] ?? ""}
                        onChange={(e) =>
                          setMontantsDep((m) => ({ ...m, [c.id]: clampNonNegative(e.target.value) }))
                        }
                      />
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <div className="flex items-baseline justify-between gap-3">
                  <p className="text-sm font-medium text-emerald-700">{t("tabRecettes")}</p>
                  <p className="text-xs text-slate-500">
                    {t("totalPrevu", { total: fmtMoney(totalRecPrevu) })}
                  </p>
                </div>
                <div className="mt-2 space-y-2">
                  {recCats.map((c) => (
                    <div key={c.id} className="flex items-center justify-between gap-2">
                      <span className="text-sm text-slate-700">{c.libelle}</span>
                      <Input
                        className="w-28 text-right"
                        type="number"
                        inputMode="decimal"
                        min={0}
                        step="0.01"
                        placeholder="0"
                        value={montantsRec[c.id] ?? ""}
                        onChange={(e) =>
                          setMontantsRec((m) => ({ ...m, [c.id]: clampNonNegative(e.target.value) }))
                        }
                      />
                    </div>
                  ))}
                </div>
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="outline" onClick={() => setCreateOpen(false)}>
                {tc("cancel")}
              </Button>
              <Button
                onClick={() => mutCreer.mutate()}
                disabled={mutCreer.isPending}
              >
                {tc("create")}
              </Button>
            </div>
            {mutCreer.isError && (
              <p className="mt-2 text-sm text-red-600">{createErrorMessage}</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
