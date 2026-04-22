"use client";

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import type { PaieResponse } from "@/lib/types/rh";
import type { MarquerPayeRequest } from "@/lib/types/rh";
import { useAuthStore } from "@/lib/store";
import { annulerPaie, listPaieOrganisation, marquerPaye } from "@/services/paie.service";

function fmtMoney(v: string | number, devise: string) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency: devise || "EUR" }).format(n);
}

export default function PaieOrganisationPage() {
  const t = useTranslations("RH.paie");
  const tc = useTranslations("Common");
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const canMark = user?.role === "RH" || user?.role === "FINANCIER" || user?.role === "ADMIN";
  const [annee, setAnnee] = useState(() => new Date().getFullYear());
  const [page, setPage] = useState(0);
  const [markOpen, setMarkOpen] = useState(false);
  const [markTarget, setMarkTarget] = useState<PaieResponse | null>(null);
  const [markForm, setMarkForm] = useState<MarquerPayeRequest>({
    datePaiement: new Date().toISOString().slice(0, 10),
    modePaiement: "VIREMENT",
    notes: "",
  });
  const [cancelId, setCancelId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["rh", "paie-org", annee, page],
    queryFn: () => listPaieOrganisation(annee, { page, size: 24 }),
  });

  const rows = data?.content ?? [];

  const mutMarkPaid = useMutation({
    mutationFn: ({ id, body }: { id: string; body: MarquerPayeRequest }) => marquerPaye(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "paie-org"] });
      setMarkOpen(false);
      setMarkTarget(null);
    },
  });

  const mutCancel = useMutation({
    mutationFn: (id: string) => annulerPaie(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "paie-org"] });
      setCancelId(null);
    },
  });

  function openMarkPaid(p: PaieResponse) {
    setMarkTarget(p);
    setMarkForm({
      datePaiement: new Date().toISOString().slice(0, 10),
      modePaiement: "VIREMENT",
      notes: "",
    });
    setMarkOpen(true);
  }

  const columns = useMemo<ColumnDef<PaieResponse>[]>(
    () => [
      { accessorKey: "salarieNomComplet", header: "Salarié" },
      { accessorKey: "matricule", header: "Matricule" },
      {
        id: "periode",
        header: "Période",
        cell: ({ row }) => (
          <span>
            {row.original.mois}/{row.original.annee}
          </span>
        ),
      },
      {
        id: "montant",
        header: "Montant",
        cell: ({ row }) => <span>{fmtMoney(row.original.montant, row.original.devise)}</span>,
      },
      { accessorKey: "statut", header: "Statut" },
      ...(canMark
        ? ([
            {
              id: "actions",
              header: () => <div className="text-right">Actions</div>,
              cell: ({ row }) => {
                const p = row.original;
                if (p.statut !== "EN_ATTENTE") {
                  return <div className="text-right text-slate-400">—</div>;
                }
                return (
                  <div className="text-right">
                    <div className="inline-flex items-center justify-end gap-2">
                      <Button type="button" size="sm" variant="secondary" onClick={() => openMarkPaid(p)}>
                        Marquer payé
                      </Button>
                      <Button type="button" size="sm" variant="outline" onClick={() => setCancelId(p.id)}>
                        Annuler
                      </Button>
                    </div>
                  </div>
                );
              },
            },
          ] as ColumnDef<PaieResponse>[])
        : []),
    ],
    [canMark]
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">{t("title")}</h1>
          <p className="text-sm text-slate-600">{t("subtitle")}</p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-sm text-slate-600" htmlFor="annee-paie">
            Année
          </label>
          <Input
            id="annee-paie"
            type="number"
            className="w-28"
            value={annee}
            onChange={(e) => {
              setPage(0);
              setAnnee(Number(e.target.value) || new Date().getFullYear());
            }}
          />
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
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={columns.length}>{tc("loading")}</TableCell>
              </TableRow>
            ) : table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length}>{tc("emptyTable")}</TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex justify-between text-sm">
          <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            Précédent
          </Button>
          <Button type="button" variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            Suivant
          </Button>
        </div>
      )}

      {markOpen && markTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-4 shadow-xl">
            <h3 className="mb-1 font-semibold">Marquer comme payé</h3>
            <p className="mb-3 text-xs text-slate-600">
              {markTarget.salarieNomComplet} · {markTarget.mois}/{markTarget.annee} · {fmtMoney(markTarget.montant, markTarget.devise)}
            </p>
            <div className="space-y-2">
              <div>
                <Label>Date paiement</Label>
                <Input
                  type="date"
                  value={markForm.datePaiement}
                  onChange={(e) => setMarkForm((f) => ({ ...f, datePaiement: e.target.value }))}
                />
              </div>
              <div>
                <Label>Mode</Label>
                <Input value={markForm.modePaiement} onChange={(e) => setMarkForm((f) => ({ ...f, modePaiement: e.target.value }))} />
              </div>
              <div>
                <Label>Notes</Label>
                <Input value={markForm.notes ?? ""} onChange={(e) => setMarkForm((f) => ({ ...f, notes: e.target.value }))} />
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setMarkOpen(false)}>
                Annuler
              </Button>
              <Button
                type="button"
                disabled={mutMarkPaid.isPending}
                onClick={() => mutMarkPaid.mutate({ id: markTarget.id, body: markForm })}
              >
                Confirmer
              </Button>
            </div>
          </div>
        </div>
      )}

      {cancelId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-4 shadow-xl">
            <h3 className="mb-1 font-semibold">Annuler le paiement</h3>
            <p className="mb-3 text-xs text-slate-600">Cette action passera le paiement au statut ANNULE.</p>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setCancelId(null)}>
                Retour
              </Button>
              <Button type="button" disabled={mutCancel.isPending} onClick={() => mutCancel.mutate(cancelId)}>
                Confirmer l&apos;annulation
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
