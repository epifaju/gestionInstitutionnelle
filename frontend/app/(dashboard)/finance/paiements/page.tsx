"use client";

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import type { PaiementResponse } from "@/lib/types/finance";
import { listPaiements } from "@/services/finance.service";

function fmt(v: string | number) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  return Number.isNaN(n) ? String(v) : n.toFixed(2);
}

export default function PaiementsPage() {
  const t = useTranslations("Finance.paiements");
  const tc = useTranslations("Common");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["finance", "paiements", page],
    queryFn: () => listPaiements({ page, size: 20 }),
  });

  const rows = data?.content ?? [];

  const columns = useMemo<ColumnDef<PaiementResponse>[]>(
    () => [
      {
        accessorKey: "datePaiement",
        header: t("thDate"),
      },
      {
        id: "montant",
        header: t("thMontant"),
        cell: ({ row }) => (
          <span>
            {fmt(row.original.montantTotal)} {row.original.devise}
          </span>
        ),
      },
      { accessorKey: "moyenPaiement", header: t("thMoyen") },
      { accessorKey: "compte", header: t("thCompte"), cell: ({ row }) => row.original.compte ?? "—" },
    ],
    [t]
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{t("title")}</h1>
        <p className="text-sm text-slate-600">{t("subtitle")}</p>
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
            {tc("previous")}
          </Button>
          <Button type="button" variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            {tc("next")}
          </Button>
        </div>
      )}
    </div>
  );
}
