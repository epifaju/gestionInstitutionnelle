"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuthStore } from "@/lib/store";
import { intlLocaleTag } from "@/lib/intl-locale";
import {
  downloadBilanAnnuelExcel,
  downloadBilanMensuelPdf,
  downloadBlob,
  downloadExportCsv,
  getBilanMensuel,
} from "@/services/rapports.service";

function n(s: string | number) {
  const x = typeof s === "number" ? s : parseFloat(String(s));
  return Number.isFinite(x) ? x : 0;
}

export default function RapportsPage() {
  const t = useTranslations("Rapports");
  const localeTag = intlLocaleTag(useLocale());
  const user = useAuthStore((s) => s.user);
  const canFinance = user?.role === "FINANCIER" || user?.role === "ADMIN";

  const now = new Date();
  const [annee, setAnnee] = useState(now.getFullYear());
  const [mois, setMois] = useState(now.getMonth() + 1);
  const [anneeExcel, setAnneeExcel] = useState(now.getFullYear());
  const [csvType, setCsvType] = useState<"factures" | "recettes" | "salaires">("factures");

  const { data: bilan, isLoading } = useQuery({
    queryKey: ["rapports", "bilan", annee, mois],
    queryFn: () => getBilanMensuel(annee, mois),
    enabled: canFinance,
  });

  function fmtMoney(v: number) {
    return new Intl.NumberFormat(localeTag, { style: "currency", currency: "EUR" }).format(v);
  }

  async function handlePdf() {
    const blob = await downloadBilanMensuelPdf(annee, mois);
    downloadBlob(blob, `bilan-${annee}-${String(mois).padStart(2, "0")}.pdf`);
  }

  async function handleExcel() {
    const blob = await downloadBilanAnnuelExcel(anneeExcel);
    downloadBlob(blob, `bilan-annuel-${anneeExcel}.xlsx`);
  }

  async function handleCsv() {
    const blob = await downloadExportCsv(csvType);
    downloadBlob(blob, `export-${csvType}.csv`);
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t("subtitleLong")}</p>
      </div>

      {canFinance && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t("exportsTitle")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-4">
            <div className="space-y-1">
              <Label>{t("monthlyPdfLabel")}</Label>
              <div className="flex flex-wrap items-end gap-2">
                <select
                  className="rounded-md border border-border bg-background px-2 py-2 text-sm text-foreground"
                  value={mois}
                  onChange={(e) => setMois(parseInt(e.target.value, 10))}
                >
                  {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                    <option key={m} value={m}>
                      {String(m).padStart(2, "0")}
                    </option>
                  ))}
                </select>
                <select
                  className="rounded-md border border-border bg-background px-2 py-2 text-sm text-foreground"
                  value={annee}
                  onChange={(e) => setAnnee(parseInt(e.target.value, 10))}
                >
                  {[annee - 1, annee, annee + 1].map((y) => (
                    <option key={y} value={y}>
                      {y}
                    </option>
                  ))}
                </select>
                <Button type="button" variant="secondary" onClick={handlePdf}>
                  {t("downloadPdf")}
                </Button>
              </div>
            </div>

            <div className="space-y-1">
              <Label>{t("annualExcelLabel")}</Label>
              <div className="flex flex-wrap items-end gap-2">
                <select
                  className="rounded-md border border-border bg-background px-2 py-2 text-sm text-foreground"
                  value={anneeExcel}
                  onChange={(e) => setAnneeExcel(parseInt(e.target.value, 10))}
                >
                  {[anneeExcel - 1, anneeExcel, anneeExcel + 1].map((y) => (
                    <option key={y} value={y}>
                      {y}
                    </option>
                  ))}
                </select>
                <Button type="button" variant="secondary" onClick={handleExcel}>
                  {t("downloadExcel")}
                </Button>
              </div>
            </div>

            <div className="space-y-1">
              <Label>{t("csvExportLabel")}</Label>
              <div className="flex flex-wrap items-end gap-2">
                <select
                  className="rounded-md border border-border bg-background px-2 py-2 text-sm text-foreground"
                  value={csvType}
                  onChange={(e) => setCsvType(e.target.value as typeof csvType)}
                >
                  <option value="factures">{t("csvTypeFactures")}</option>
                  <option value="recettes">{t("csvTypeRecettes")}</option>
                  <option value="salaires">{t("csvTypeSalaires")}</option>
                </select>
                <Button type="button" variant="outline" onClick={handleCsv}>
                  {t("downloadCsv")}
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {canFinance && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t("previewTitle")}</CardTitle>
            <p className="text-sm text-muted-foreground">
              {String(mois).padStart(2, "0")}/{annee}
            </p>
          </CardHeader>
          <CardContent>
            {isLoading || !bilan ? (
              <Skeleton className="h-40 w-full" />
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <div>
                  <p className="text-xs uppercase text-muted-foreground">{t("kpiDepenses")}</p>
                  <p className="text-xl font-semibold text-rose-600 dark:text-rose-400">{fmtMoney(n(bilan.totalDepenses))}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-muted-foreground">{t("kpiRecettes")}</p>
                  <p className="text-xl font-semibold text-emerald-600 dark:text-emerald-400">{fmtMoney(n(bilan.totalRecettes))}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-muted-foreground">{t("kpiSolde")}</p>
                  <p className="text-xl font-semibold text-sky-700 dark:text-sky-400">{fmtMoney(n(bilan.solde))}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-muted-foreground">{t("kpiFactures")}</p>
                  <p className="text-lg font-medium">{bilan.nbFactures}</p>
                  <p className="text-xs text-muted-foreground">{t("kpiFacturesEnAttente", { count: bilan.nbFacturesEnAttente })}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-muted-foreground">{t("kpiEffectifsActifs")}</p>
                  <p className="text-lg font-medium">{bilan.effectifsActifs}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-muted-foreground">{t("kpiCongesPeriode")}</p>
                  <p className="text-lg font-medium">{bilan.nbCongesDuMois}</p>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {!canFinance && (
        <p className="text-sm text-muted-foreground">
          {t("restrictedHint")}
        </p>
      )}
    </div>
  );
}
