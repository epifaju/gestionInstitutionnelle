"use client";

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RecetteModal } from "@/components/finance/RecetteModal";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { RecetteRequest, RecetteResponse } from "@/lib/types/finance";
import { useAuthStore } from "@/lib/store";
import { createRecette, deleteRecette, listCategories, listRecettes, updateRecette, uploadJustificatifRecette } from "@/services/finance.service";

function fmt(v: string | number) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  return Number.isNaN(n) ? String(v) : n.toFixed(2);
}

export default function RecettesPage() {
  const qc = useQueryClient();
  const role = useAuthStore((s) => s.user?.role);
  const isFin = role === "FINANCIER";
  const isAdmin = role === "ADMIN";
  const canCreate = isFin || isAdmin;
  const canEdit = isFin || isAdmin;
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<RecetteResponse | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState<RecetteResponse | null>(null);
  const { data: categories } = useQuery({ queryKey: ["finance", "categories"], queryFn: listCategories });
  const { data, isLoading } = useQuery({
    queryKey: ["finance", "recettes", page],
    queryFn: () => listRecettes({ page, size: 20 }),
  });

  const mut = useMutation({
    mutationFn: ({ req, file }: { req: RecetteRequest; file: File | null }) => createRecette(req, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["finance", "recettes"] }),
  });

  const mutEdit = useMutation({
    mutationFn: ({ id, req }: { id: string; req: RecetteRequest }) => updateRecette(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["finance", "recettes"] }),
  });

  const mutUploadJustif = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => uploadJustificatifRecette(id, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["finance", "recettes"] }),
  });

  const mutDelete = useMutation({
    mutationFn: (id: string) => deleteRecette(id),
    onSuccess: () => {
      setDeleteOpen(false);
      setDeleting(null);
      qc.invalidateQueries({ queryKey: ["finance", "recettes"] });
    },
  });

  const rows = data?.content ?? [];

  const columns = useMemo<ColumnDef<RecetteResponse>[]>(
    () => [
      { accessorKey: "dateRecette", header: "Date" },
      { accessorKey: "typeRecette", header: "Type" },
      {
        accessorKey: "description",
        header: "Description",
        cell: ({ row }) => row.original.description ?? "—",
      },
      {
        id: "m",
        header: "Montant",
        cell: ({ row }) => <span>{fmt(row.original.montant)}</span>,
      },
      { accessorKey: "devise", header: "Devise" },
      {
        accessorKey: "categorieLibelle",
        header: "Catégorie",
        cell: ({ row }) => row.original.categorieLibelle ?? "—",
      },
      {
        id: "actions",
        header: "Actions",
        cell: ({ row }) => {
          const url = row.original.justificatifUrl;
          return (
            <div className="flex items-center justify-end gap-2">
              {canEdit && (
                <Button
                  variant="secondary"
                  size="sm"
                  type="button"
                  onClick={() => {
                    setEditing(row.original);
                    setEditOpen(true);
                  }}
                >
                  Modifier
                </Button>
              )}
              {canEdit && (
                <Button
                  variant="destructive"
                  size="sm"
                  type="button"
                  onClick={() => {
                    setDeleting(row.original);
                    setDeleteOpen(true);
                  }}
                >
                  Supprimer
                </Button>
              )}
              {url ? (
              <Button
                variant="outline"
                size="sm"
                type="button"
                onClick={() => window.open(url, "_blank", "noopener,noreferrer")}
              >
                Justificatif
              </Button>
              ) : (
                <span className="text-slate-400">—</span>
              )}
            </div>
          );
        },
      },
    ],
    [canEdit]
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Recettes</h1>
          <p className="text-sm text-slate-600">Encaissements.</p>
        </div>
        {canCreate && (
          <Button type="button" onClick={() => setOpen(true)}>
            + Nouvelle recette
          </Button>
        )}
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
                <TableCell colSpan={7}>Chargement…</TableCell>
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

      <RecetteModal
        open={open}
        onClose={() => setOpen(false)}
        categories={categories ?? []}
        onSubmit={async (req, file) => {
          await mut.mutateAsync({ req, file });
        }}
      />

      <RecetteModal
        open={editOpen}
        onClose={() => {
          setEditOpen(false);
          setEditing(null);
        }}
        title={editing ? `Modifier la recette` : "Modifier la recette"}
        submitLabel="Enregistrer"
        allowFile
        initial={
          editing
            ? {
                dateRecette: editing.dateRecette,
                montant: typeof editing.montant === "string" ? parseFloat(editing.montant) : editing.montant,
                devise: editing.devise,
                typeRecette: editing.typeRecette,
                description: editing.description ?? "",
                modeEncaissement: editing.modeEncaissement ?? "",
                categorieId: editing.categorieId ?? null,
              }
            : null
        }
        categories={categories ?? []}
        onSubmit={async (req, file) => {
          if (!editing) return;
          await mutEdit.mutateAsync({ id: editing.id, req });
          if (file) {
            await mutUploadJustif.mutateAsync({ id: editing.id, file });
          }
        }}
      />

      {deleteOpen && deleting && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-lg">
            <h2 className="text-lg font-semibold text-slate-900">Supprimer la recette</h2>
            <p className="mt-2 text-sm text-slate-600">
              Confirmez la suppression de la recette du{" "}
              <span className="font-medium">{deleting.dateRecette}</span> pour{" "}
              <span className="font-medium">{fmt(deleting.montant)}</span> {deleting.devise}.
            </p>
            <p className="mt-2 text-xs text-slate-500">Le justificatif associé (s’il existe) sera aussi supprimé.</p>

            <div className="mt-6 flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={mutDelete.isPending}
                onClick={() => {
                  setDeleteOpen(false);
                  setDeleting(null);
                }}
              >
                Annuler
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={mutDelete.isPending}
                onClick={() => mutDelete.mutate(deleting.id)}
              >
                {mutDelete.isPending ? "Suppression…" : "Supprimer"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
