"use client";

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { CongeResponse } from "@/lib/types/rh";
import {
  annulerCongeValide,
  getCalendrier,
  listConges,
  rejeterConge,
  soumettreConge,
  validerConge,
} from "@/services/conge.service";
import { CongeForm } from "@/components/forms/CongeForm";
import { useAuthStore } from "@/lib/store";
import { getMySalarie } from "@/services/salarie.service";

const typeColor: Record<string, string> = {
  ANNUEL: "bg-sky-200 text-sky-900",
  MALADIE: "bg-red-200 text-red-900",
  EXCEPTIONNEL: "bg-orange-200 text-orange-900",
  SANS_SOLDE: "bg-slate-300 text-slate-800",
};

function startOfMonth(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

function endOfMonth(d: Date) {
  return new Date(d.getFullYear(), d.getMonth() + 1, 0);
}

function fmtIso(d: Date) {
  return d.toISOString().slice(0, 10);
}

/** Congé validé annulable seulement si la date de début est strictement après aujourd'hui (règle métier). */
function canAnnulerCongeValide(dateDebutIso: string) {
  const start = new Date(dateDebutIso + "T12:00:00");
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  start.setHours(0, 0, 0, 0);
  return start.getTime() > today.getTime();
}

export default function CongesPage() {
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const isRhOrAdmin = user?.role === "RH" || user?.role === "ADMIN";
  const isEmploye = user?.role === "EMPLOYE";
  const [view, setView] = useState<"liste" | "cal">("liste");
  const [page, setPage] = useState(0);
  const [statut, setStatut] = useState("");
  const [typeConge, setTypeConge] = useState("");
  const [cursor, setCursor] = useState(() => new Date());
  const [confirmValidateId, setConfirmValidateId] = useState<string | null>(null);
  const [rejectId, setRejectId] = useState<string | null>(null);
  const [motif, setMotif] = useState("");

  const debut = startOfMonth(cursor);
  const fin = endOfMonth(cursor);

  const { data: liste } = useQuery({
    queryKey: ["rh", "conges", "list", page, statut, typeConge],
    queryFn: () =>
      listConges({
        page,
        size: 25,
        statut: statut || undefined,
        typeConge: typeConge || undefined,
      }),
    enabled: view === "liste",
  });

  const { data: events } = useQuery({
    queryKey: ["rh", "conges", "cal", fmtIso(debut), fmtIso(fin)],
    queryFn: () => getCalendrier(fmtIso(debut), fmtIso(fin)),
    enabled: view === "cal",
  });

  const { data: mySalarie } = useQuery({
    queryKey: ["rh", "me", "salarie"],
    queryFn: () => getMySalarie(),
    enabled: isEmploye && view === "liste",
  });

  const mutVal = useMutation({
    mutationFn: (id: string) => validerConge(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "conges"] });
    },
  });

  const mutRej = useMutation({
    mutationFn: ({ id, motifRejet }: { id: string; motifRejet: string }) => rejeterConge(id, { motifRejet }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "conges"] });
      setRejectId(null);
      setMotif("");
    },
  });

  const mutAnnul = useMutation({
    mutationFn: (id: string) => annulerCongeValide(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "conges"] });
    },
  });

  const mutSoumettre = useMutation({
    mutationFn: (body: Parameters<typeof soumettreConge>[0]) => soumettreConge(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "conges"] });
    },
  });

  const rows = liste?.content ?? [];

  const columns = useMemo<ColumnDef<CongeResponse>[]>(
    () => [
      {
        accessorKey: "salarieNomComplet",
        header: "Salarié",
      },
      { accessorKey: "service", header: "Service" },
      { accessorKey: "typeConge", header: "Type" },
      {
        id: "periode",
        header: "Période",
        cell: ({ row }) => (
          <span>
            {row.original.dateDebut} → {row.original.dateFin}
          </span>
        ),
      },
      { accessorKey: "nbJours", header: "Jours" },
      {
        accessorKey: "statut",
        header: "Statut",
        cell: ({ row }) => <Badge>{row.original.statut}</Badge>,
      },
      {
        id: "actions",
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const r = row.original;
          if (isRhOrAdmin && r.statut === "EN_ATTENTE") {
            return (
              <div className="flex justify-end gap-2">
                <Button type="button" size="sm" variant="outline" onClick={() => setConfirmValidateId(r.id)}>
                  Valider
                </Button>
                <Button type="button" size="sm" variant="outline" onClick={() => setRejectId(r.id)}>
                  Rejeter
                </Button>
              </div>
            );
          }
          if (isRhOrAdmin && r.statut === "VALIDE" && canAnnulerCongeValide(r.dateDebut)) {
            return (
              <div className="flex justify-end">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={mutAnnul.isPending}
                  onClick={() => mutAnnul.mutate(r.id)}
                >
                  Annuler
                </Button>
              </div>
            );
          }
          return <div className="text-right text-slate-400">—</div>;
        },
      },
    ],
    [isRhOrAdmin, mutAnnul]
  );

  const table = useReactTable({
    data: rows,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  const firstWeekday = debut.getDay();
  const offset = firstWeekday === 0 ? 6 : firstWeekday - 1;
  const daysInMonth = fin.getDate();

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Congés</h1>
          <p className="text-sm text-slate-600">Demandes et calendrier.</p>
        </div>
        <div className="flex gap-2">
          <Button type="button" variant={view === "liste" ? "default" : "outline"} size="sm" onClick={() => setView("liste")}>
            Liste
          </Button>
          <Button type="button" variant={view === "cal" ? "default" : "outline"} size="sm" onClick={() => setView("cal")}>
            Calendrier
          </Button>
        </div>
      </div>

      {view === "liste" && (
        <>
          {isEmploye && mySalarie?.id ? (
            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <h2 className="mb-2 font-semibold text-slate-900">Nouvelle demande</h2>
              <CongeForm
                salarieId={mySalarie.id}
                onSubmit={async (b) => {
                  await mutSoumettre.mutateAsync(b);
                }}
              />
            </div>
          ) : null}
          <div className="flex flex-wrap gap-2 rounded-lg border border-slate-200 bg-white p-3">
            <div>
              <Label className="text-xs text-slate-500">Statut</Label>
              <select
                className="flex h-9 w-40 rounded-md border border-slate-200 bg-white px-2 text-sm"
                value={statut}
                onChange={(e) => {
                  setStatut(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Tous</option>
                <option value="EN_ATTENTE">EN_ATTENTE</option>
                <option value="VALIDE">VALIDE</option>
                <option value="REJETE">REJETE</option>
                <option value="ANNULE">ANNULE</option>
              </select>
            </div>
            <div>
              <Label className="text-xs text-slate-500">Type</Label>
              <select
                className="flex h-9 w-44 rounded-md border border-slate-200 bg-white px-2 text-sm"
                value={typeConge}
                onChange={(e) => {
                  setTypeConge(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Tous</option>
                <option value="ANNUEL">ANNUEL</option>
                <option value="MALADIE">MALADIE</option>
                <option value="EXCEPTIONNEL">EXCEPTIONNEL</option>
                <option value="SANS_SOLDE">SANS_SOLDE</option>
              </select>
            </div>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white">
            <Table>
              <TableHeader>
                {table.getHeaderGroups().map((hg) => (
                  <TableRow key={hg.id}>
                    {hg.headers.map((h) => (
                      <TableHead key={h.id}>{flexRender(h.column.columnDef.header, h.getContext())}</TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.map((row) => (
                  <TableRow key={row.id}>
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          {liste && liste.totalPages > 1 && (
            <div className="flex justify-between text-sm text-slate-600">
              <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                Précédent
              </Button>
              <Button type="button" variant="outline" size="sm" disabled={liste.last} onClick={() => setPage((p) => p + 1)}>
                Suivant
              </Button>
            </div>
          )}
        </>
      )}

      {view === "cal" && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="mb-3 flex items-center justify-between">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() - 1, 1))}
            >
              ←
            </Button>
            <span className="font-semibold capitalize">
              {cursor.toLocaleString("fr-FR", { month: "long", year: "numeric" })}
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1))}
            >
              →
            </Button>
          </div>
          <div className="grid grid-cols-7 gap-1 text-center text-xs font-medium text-slate-500">
            {["Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"].map((d) => (
              <div key={d}>{d}</div>
            ))}
          </div>
          <div className="mt-1 grid grid-cols-7 gap-1">
            {Array.from({ length: offset }).map((_, i) => (
              <div key={`e-${i}`} className="min-h-[72px] rounded border border-transparent bg-slate-50/50" />
            ))}
            {Array.from({ length: daysInMonth }).map((_, i) => {
              const day = i + 1;
              const iso = fmtIso(new Date(cursor.getFullYear(), cursor.getMonth(), day));
              const dayEvents = dedupe((events ?? []).filter((e) => e.dateDebut <= iso && e.dateFin >= iso));
              return (
                <div key={day} className="min-h-[72px] rounded border border-slate-100 bg-white p-1 text-left text-[10px]">
                  <div className="font-semibold text-slate-800">{day}</div>
                  {dayEvents.slice(0, 3).map((c) => (
                    <div key={c.id} className={`mt-0.5 truncate rounded px-0.5 ${typeColor[c.typeConge] ?? "bg-slate-200"}`}>
                      {c.typeConge}
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {rejectId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-4 shadow-xl">
            <h3 className="mb-1 font-semibold">Rejeter la demande</h3>
            <p className="mb-2 text-xs text-slate-600">Confirmez le rejet en indiquant un motif.</p>
            <Input value={motif} onChange={(e) => setMotif(e.target.value)} placeholder="Obligatoire" />
            <div className="mt-3 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setRejectId(null)}>
                Annuler
              </Button>
              <Button
                type="button"
                disabled={!motif.trim() || mutRej.isPending}
                onClick={() => mutRej.mutate({ id: rejectId, motifRejet: motif.trim() })}
              >
                Confirmer le rejet
              </Button>
            </div>
          </div>
        </div>
      )}

      {confirmValidateId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-4 shadow-xl">
            <h3 className="mb-1 font-semibold">Valider la demande</h3>
            <p className="mb-3 text-xs text-slate-600">Cette action passera la demande au statut VALIDE.</p>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setConfirmValidateId(null)}>
                Annuler
              </Button>
              <Button
                type="button"
                disabled={mutVal.isPending}
                onClick={() => mutVal.mutate(confirmValidateId, { onSuccess: () => setConfirmValidateId(null) })}
              >
                Confirmer la validation
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function dedupe(list: CongeResponse[]) {
  const m = new Map<string, CongeResponse>();
  for (const c of list) m.set(c.id, c);
  return Array.from(m.values());
}
