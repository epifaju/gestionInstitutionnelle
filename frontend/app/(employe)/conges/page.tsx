"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getEmployeDroitsConges, listEmployeConges, submitEmployeConge } from "@/services/employe.service";
import type { CongeResponse } from "@/lib/types/rh";

const TYPES = [
  { key: "ANNUEL", label: "Annuel" },
  { key: "EXCEPTIONNEL", label: "Exceptionnel" },
  { key: "MALADIE", label: "Maladie" },
  { key: "SANS_SOLDE", label: "Sans solde" },
];

function badge(statut: string) {
  const base = "inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold";
  if (statut === "VALIDE") return `${base} bg-emerald-50 text-emerald-700`;
  if (statut === "REJETE") return `${base} bg-rose-50 text-rose-700`;
  if (statut === "EN_ATTENTE") return `${base} bg-amber-50 text-amber-700`;
  return `${base} bg-slate-100 text-slate-700`;
}

export default function EmployeCongesPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [typeConge, setTypeConge] = useState("ANNUEL");
  const [dateDebut, setDateDebut] = useState("");
  const [dateFin, setDateFin] = useState("");
  const [commentaire, setCommentaire] = useState("");

  const droitsQ = useQuery({ queryKey: ["employe", "droits"], queryFn: () => getEmployeDroitsConges() });
  const congesQ = useQuery({ queryKey: ["employe", "conges"], queryFn: () => listEmployeConges({ page: 0, size: 50 }) });

  const mut = useMutation({
    mutationFn: () => submitEmployeConge({ typeConge, dateDebut, dateFin, commentaire }),
    onSuccess: () => {
      toast.success("Demande envoyée.");
      setOpen(false);
      setCommentaire("");
      qc.invalidateQueries({ queryKey: ["employe", "conges"] });
      qc.invalidateQueries({ queryKey: ["employe", "droits"] });
    },
    onError: () => toast.error("Impossible d'envoyer la demande."),
  });

  const timeline: CongeResponse[] = useMemo(() => congesQ.data?.content ?? [], [congesQ.data?.content]);

  return (
    <div className="mx-auto max-w-md space-y-4">
      <div className="rounded-2xl border border-slate-200 bg-white p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Solde annuel</p>
        <p className="mt-2 text-3xl font-bold text-slate-900">{droitsQ.data?.joursRestants ?? "—"}</p>
        <p className="text-sm text-slate-600">
          Droit: {droitsQ.data?.joursDroit ?? "—"} · Pris: {droitsQ.data?.joursPris ?? "—"}
        </p>
      </div>

      <div className="space-y-2">
        {timeline.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-600">Aucune demande.</div>
        ) : (
          timeline.map((c) => (
            <div key={c.id} className="rounded-2xl border border-slate-200 bg-white p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-slate-900">{c.typeConge}</p>
                  <p className="text-xs text-slate-600">
                    {c.dateDebut} → {c.dateFin} · {c.nbJours} j
                  </p>
                </div>
                <span className={badge(c.statut)}>{c.statut}</span>
              </div>
              {c.motifRejet ? <p className="mt-2 text-xs text-rose-700">{c.motifRejet}</p> : null}
            </div>
          ))
        )}
      </div>

      <button
        type="button"
        className="fixed bottom-20 right-4 z-40 h-14 w-14 rounded-full bg-indigo-600 text-white shadow-lg active:scale-[0.98]"
        aria-label="Nouvelle demande"
        onClick={() => setOpen(true)}
      >
        +
      </button>

      {open ? (
        <div className="fixed inset-0 z-50">
          <button type="button" className="absolute inset-0 bg-slate-900/40" onClick={() => setOpen(false)} aria-label="Fermer" />
          <div className="absolute bottom-0 left-0 right-0 rounded-t-2xl bg-white p-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <p className="text-sm font-semibold text-slate-900">Nouvelle demande</p>
              <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
                Fermer
              </Button>
            </div>

            <div className="mt-3 grid gap-3">
              <div>
                <Label>Type</Label>
                <div className="mt-2 grid grid-cols-2 gap-2">
                  {TYPES.map((t) => (
                    <button
                      key={t.key}
                      type="button"
                      className={`rounded-xl border px-3 py-2 text-sm ${
                        typeConge === t.key ? "border-indigo-600 bg-indigo-50 text-indigo-700" : "border-slate-200 bg-white text-slate-700"
                      }`}
                      onClick={() => setTypeConge(t.key)}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <Label>Début</Label>
                  <Input type="date" value={dateDebut} onChange={(e) => setDateDebut(e.target.value)} />
                </div>
                <div>
                  <Label>Fin</Label>
                  <Input type="date" value={dateFin} onChange={(e) => setDateFin(e.target.value)} />
                </div>
              </div>
              <div>
                <Label>Commentaire</Label>
                <Input value={commentaire} onChange={(e) => setCommentaire(e.target.value)} placeholder="Optionnel" />
              </div>
              <Button type="button" disabled={mut.isPending || !dateDebut || !dateFin} onClick={() => mut.mutate()}>
                {mut.isPending ? "Envoi…" : "Soumettre la demande"}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

