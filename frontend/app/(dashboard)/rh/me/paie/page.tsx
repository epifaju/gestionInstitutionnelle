"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { flexRender, getCoreRowModel, useReactTable } from "@tanstack/react-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuthStore } from "@/lib/store";
import type { PaieResponse } from "@/lib/types/rh";
import { listMyPaie } from "@/services/paie.service";

function fmtMoney(v: string | number, devise: string) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency: devise || "EUR" }).format(n);
}

function paieVariant(s: string): "success" | "warning" | "default" {
  if (s === "PAYE") return "success";
  if (s === "EN_ATTENTE") return "warning";
  return "default";
}

export default function MyPaiePage() {
  const tc = useTranslations("Common");
  const user = useAuthStore((s) => s.user);
  const [annee, setAnnee] = useState(() => new Date().getFullYear());
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["rh", "my-paie", annee, page],
    queryFn: () => listMyPaie(annee, { page, size: 12 }),
  });

  const rows = data?.content ?? [];

  const columns = useMemo<ColumnDef<PaieResponse>[]>(
    () => [
      { accessorKey: "mois", header: "Mois" },
      {
        id: "montant",
        header: "Montant",
        cell: ({ row }) => <span>{fmtMoney(row.original.montant, row.original.devise)}</span>,
      },
      {
        accessorKey: "statut",
        header: "Statut",
        cell: ({ row }) => <Badge variant={paieVariant(row.original.statut)}>{row.original.statut}</Badge>,
      },
      { accessorKey: "datePaiement", header: "Date paiement", cell: ({ row }) => row.original.datePaiement ?? "—" },
      { accessorKey: "modePaiement", header: "Mode", cell: ({ row }) => row.original.modePaiement ?? "—" },
    ],
    []
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Ma paie</h1>
          <p className="text-sm text-slate-600">
            {user?.prenom ?? ""} {user?.nom ?? ""}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-sm text-slate-600" htmlFor="annee-my-paie">
            Année
          </label>
          <Input
            id="annee-my-paie"
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
    </div>
  );
}

