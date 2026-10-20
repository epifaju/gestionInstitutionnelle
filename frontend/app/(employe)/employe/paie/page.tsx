"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { listEmployePaie, downloadEmployeFichePdf } from "@/services/employe.service";

function badge(statut: string) {
  const base = "inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold";
  if (statut === "PAYE") return `${base} bg-emerald-50 text-emerald-700`;
  if (statut === "EN_ATTENTE") return `${base} bg-muted text-foreground`;
  return `${base} bg-muted text-foreground`;
}

export default function EmployePaiePage() {
  const currentYear = useMemo(() => new Date().getFullYear(), []);
  const [annee, setAnnee] = useState(currentYear);

  const q = useQuery({
    queryKey: ["employe", "paie", annee],
    queryFn: () => listEmployePaie(annee, { page: 0, size: 12 }),
  });

  const rows = (q.data?.content ?? []).slice().sort((a, b) => (a.mois ?? 0) - (b.mois ?? 0));

  return (
    <div className="mx-auto max-w-md space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-foreground">Paie</h1>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" size="sm" onClick={() => setAnnee((y) => y - 1)}>
            {annee - 1}
          </Button>
          <Button type="button" variant="secondary" size="sm">
            {annee}
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={() => setAnnee((y) => y + 1)} disabled={annee >= currentYear}>
            {annee + 1}
          </Button>
        </div>
      </div>

      {q.isLoading ? (
        <div className="rounded-2xl border border-border bg-card p-4 text-sm text-muted-foreground">Chargement…</div>
      ) : rows.length === 0 ? (
        <div className="rounded-2xl border border-border bg-card p-4 text-sm text-muted-foreground">Aucune fiche.</div>
      ) : (
        <div className="space-y-2">
          {rows.map((p) => (
            <div key={p.id} className="rounded-2xl border border-border bg-card p-4 text-card-foreground">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-foreground">
                    {String(p.mois).padStart(2, "0")}/{p.annee}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    {p.montant} {p.devise}
                  </p>
                </div>
                <span className={badge(p.statut)}>{p.statut}</span>
              </div>

              {p.hasPayslip ? (
                <div className="mt-3">
                  <Button
                    type="button"
                    variant="secondary"
                    className="w-full"
                    onClick={() => {
                      window.open(downloadEmployeFichePdf(p.annee, p.mois), "_blank");
                    }}
                  >
                    Télécharger fiche PDF
                  </Button>
                </div>
              ) : null}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

