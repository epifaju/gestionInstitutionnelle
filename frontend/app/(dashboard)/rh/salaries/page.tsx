"use client";

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import Link from "next/link";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { SalarieModal } from "@/components/rh/SalarieModal";
import type { SalarieRequest, SalarieResponse } from "@/lib/types/rh";
import { createSalarie, listSalaries } from "@/services/salarie.service";

function statutVariant(s: string): "success" | "warning" | "muted" | "default" {
  if (s === "ACTIF") return "success";
  if (s === "EN_CONGE") return "warning";
  if (s === "SORTI") return "muted";
  return "default";
}

export default function SalariesPage() {
  const ts = useTranslations("RH.salaries");
  const tc = useTranslations("Common");
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [statut, setStatut] = useState<string>("");
  const [service, setService] = useState("");
  const [search, setSearch] = useState("");
  const [modal, setModal] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["rh", "salaries", page, statut, service, search],
    queryFn: () =>
      listSalaries({
        page,
        size: 20,
        statut: statut || undefined,
        service: service || undefined,
        search: search || undefined,
      }),
  });

  const mut = useMutation({
    mutationFn: (body: SalarieRequest) => createSalarie(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "salaries"] });
    },
  });

  const rows = data?.content ?? [];

  const columns = useMemo<ColumnDef<SalarieResponse>[]>(
    () => [
      { accessorKey: "matricule", header: ts("thMatricule") },
      {
        id: "nom",
        header: ts("thNomPrenom"),
        cell: ({ row }) => (
          <span>
            {row.original.nom} {row.original.prenom}
          </span>
        ),
      },
      { accessorKey: "service", header: ts("thService") },
      { accessorKey: "poste", header: ts("thPoste") },
      {
        accessorKey: "statut",
        header: ts("thStatutList"),
        cell: ({ row }) => (
          <Badge variant={statutVariant(row.original.statut)}>{row.original.statut}</Badge>
        ),
      },
      {
        id: "actions",
        header: "",
        cell: ({ row }) => (
          <Link className="text-sm font-medium text-indigo-600 hover:underline" href={`/rh/salaries/${row.original.id}`}>
            {tc("open")}
          </Link>
        ),
      },
    ],
    [tc, ts]
  );

  const table = useReactTable({
    data: rows,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{ts("title")}</h1>
          <p className="text-sm text-muted-foreground">{ts("subtitle")}</p>
        </div>
        <Button type="button" onClick={() => setModal(true)}>
          {ts("create")}
        </Button>
      </div>

      <div className="flex flex-wrap gap-2 rounded-lg border border-border bg-card p-3 text-card-foreground">
        <div>
          <label className="text-xs text-muted-foreground">{ts("filterService")}</label>
          <Input
            className="h-9 w-40"
            value={service}
            onChange={(e) => setService(e.target.value)}
            placeholder={ts("filterPlaceholder")}
          />
        </div>
        <div>
          <label className="text-xs text-muted-foreground">{ts("filterStatut")}</label>
          <select
            className="flex h-9 w-44 rounded-md border border-border bg-background px-2 text-sm text-foreground"
            value={statut}
            onChange={(e) => {
              setStatut(e.target.value);
              setPage(0);
            }}
          >
            <option value="">{tc("all")}</option>
            <option value="ACTIF">ACTIF</option>
            <option value="EN_CONGE">EN_CONGE</option>
            <option value="SORTI">SORTI</option>
            <option value="BROUILLON">BROUILLON</option>
          </select>
        </div>
        <div className="min-w-[200px] flex-1">
          <label className="text-xs text-muted-foreground">{ts("filterSearch")}</label>
          <Input
            className="h-9"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder={ts("searchPlaceholder")}
          />
        </div>
      </div>

      <div className="rounded-lg border border-border bg-card text-card-foreground">
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
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>
            {tc("page", { current: data.page + 1, total: data.totalPages })}
          </span>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={data.page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              {tc("previous")}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={data.last}
              onClick={() => setPage((p) => p + 1)}
            >
              {tc("next")}
            </Button>
          </div>
        </div>
      )}

      <SalarieModal
        open={modal}
        onClose={() => setModal(false)}
        onCreate={async (b) => {
          await mut.mutateAsync(b);
        }}
      />
    </div>
  );
}
