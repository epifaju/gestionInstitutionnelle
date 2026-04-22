"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuthStore } from "@/lib/store";
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

function fmtEur(v: number) {
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(v);
}

export default function RapportsPage() {
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
        <h1 className="text-2xl font-semibold text-slate-900">Rapports</h1>
        <p className="mt-1 text-sm text-slate-600">Exports et aperçu du bilan mensuel.</p>
      </div>

      {canFinance && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Exports</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-4">
            <div className="space-y-1">
              <Label>Bilan mensuel (PDF)</Label>
              <div className="flex flex-wrap items-end gap-2">
                <select
                  className="rounded-md border border-slate-200 px-2 py-2 text-sm"
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
                  className="rounded-md border border-slate-200 px-2 py-2 text-sm"
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
                  Télécharger PDF
                </Button>
              </div>
            </div>

            <div className="space-y-1">
              <Label>Bilan annuel (Excel)</Label>
              <div className="flex flex-wrap items-end gap-2">
                <select
                  className="rounded-md border border-slate-200 px-2 py-2 text-sm"
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
                  Télécharger Excel
                </Button>
              </div>
            </div>

            <div className="space-y-1">
              <Label>Export CSV</Label>
              <div className="flex flex-wrap items-end gap-2">
                <select
                  className="rounded-md border border-slate-200 px-2 py-2 text-sm"
                  value={csvType}
                  onChange={(e) => setCsvType(e.target.value as typeof csvType)}
                >
                  <option value="factures">Factures</option>
                  <option value="recettes">Recettes</option>
                  <option value="salaires">Salaires</option>
                </select>
                <Button type="button" variant="outline" onClick={handleCsv}>
                  Télécharger CSV
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {canFinance && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Aperçu bilan mensuel</CardTitle>
            <p className="text-sm text-slate-500">
              {String(mois).padStart(2, "0")}/{annee}
            </p>
          </CardHeader>
          <CardContent>
            {isLoading || !bilan ? (
              <Skeleton className="h-40 w-full" />
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <div>
                  <p className="text-xs uppercase text-slate-500">Dépenses</p>
                  <p className="text-xl font-semibold text-rose-600">{fmtEur(n(bilan.totalDepenses))}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-slate-500">Recettes</p>
                  <p className="text-xl font-semibold text-emerald-600">{fmtEur(n(bilan.totalRecettes))}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-slate-500">Solde</p>
                  <p className="text-xl font-semibold text-sky-700">{fmtEur(n(bilan.solde))}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-slate-500">Factures</p>
                  <p className="text-lg font-medium">{bilan.nbFactures}</p>
                  <p className="text-xs text-slate-500">En attente : {bilan.nbFacturesEnAttente}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-slate-500">Effectifs actifs</p>
                  <p className="text-lg font-medium">{bilan.effectifsActifs}</p>
                </div>
                <div>
                  <p className="text-xs uppercase text-slate-500">Congés (période)</p>
                  <p className="text-lg font-medium">{bilan.nbCongesDuMois}</p>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {!canFinance && (
        <p className="text-sm text-slate-600">
          Les exports comptables sont réservés aux rôles Financier et Administrateur.
        </p>
      )}
    </div>
  );
}
