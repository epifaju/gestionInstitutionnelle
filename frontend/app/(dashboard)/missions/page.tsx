"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useAuthStore } from "@/lib/store";
import { listMissions } from "@/services/missions.service";
import type { MissionResponse } from "@/lib/types/missions";
import type { ColumnDef } from "@tanstack/react-table";
import { flexRender, getCoreRowModel, useReactTable } from "@tanstack/react-table";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

function statutVariant(s: string): "muted" | "info" | "warning" | "success" | "dangerSolid" {
  if (s === "BROUILLON") return "muted";
  if (s === "SOUMISE") return "info";
  if (s === "APPROUVEE") return "warning";
  if (s === "EN_COURS") return "info";
  if (s === "TERMINEE") return "success";
  return "dangerSolid";
}

function fmt(v: string | number | null | undefined) {
  if (v == null) return "—";
  const n = typeof v === "string" ? parseFloat(v) : v;
  return Number.isNaN(n) ? String(v) : n.toFixed(2);
}

export default function MissionsPage() {
  const t = useTranslations("Missions");
  const tc = useTranslations("Common");
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === "ADMIN";
  const isRh = user?.role === "RH";
  const canAll = isAdmin || isRh;

  const [page, setPage] = useState(0);
  const [statut, setStatut] = useState("");
  const [debut, setDebut] = useState("");
  const [fin, setFin] = useState("");
  const [mode, setMode] = useState<"mine" | "all">("mine");

  const { data: listData, isLoading } = useQuery({
    queryKey: ["missions", "list", page, statut, debut, fin, mode],
    queryFn: () =>
      listMissions({
        page,
        size: 20,
        statut: statut || undefined,
        debut: debut || undefined,
        fin: fin || undefined,
        // backend enforces "mine" for non RH/ADMIN/FIN anyway
      }),
  });

  const rows = listData?.content ?? [];

  const columns = useMemo<ColumnDef<MissionResponse>[]>(
    () => [
      {
        accessorKey: "titre",
        header: t("thTitre"),
        cell: ({ row }) => (
          <Link className="text-indigo-700 hover:underline" href={`/missions/${row.original.id}`} onClick={(e) => e.stopPropagation()}>
            {row.original.titre}
          </Link>
        ),
      },
      { accessorKey: "destination", header: t("thDestination") },
      {
        id: "dates",
        header: t("thDates"),
        cell: ({ row }) => (
          <span className="text-sm text-slate-700">
            {row.original.dateDepart} → {row.original.dateRetour}
          </span>
        ),
      },
      { accessorKey: "nbJours", header: t("thNbJours") },
      {
        accessorKey: "statut",
        header: t("thStatut"),
        cell: ({ row }) => <Badge variant={statutVariant(row.original.statut)}>{row.original.statut}</Badge>,
      },
      {
        id: "total",
        header: t("thTotalFrais"),
        cell: ({ row }) => <span>{fmt(row.original.totalFraisValides)} EUR</span>,
      },
      {
        id: "open",
        header: "",
        cell: ({ row }) => (
          <Link href={`/missions/${row.original.id}`} className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
            {t("detailLink")}
          </Link>
        ),
      },
    ],
    [t]
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="flex gap-6">
      <aside className="hidden w-56 shrink-0 space-y-3 rounded-lg border border-slate-200 bg-white p-3 lg:block">
        <p className="text-xs font-semibold uppercase text-slate-500">{t("filters")}</p>
        <div>
          <label className="text-xs text-slate-500">{t("debut")}</label>
          <Input type="date" className="h-8 text-sm" value={debut} onChange={(e) => setDebut(e.target.value)} />
        </div>
        <div>
          <label className="text-xs text-slate-500">{t("fin")}</label>
          <Input type="date" className="h-8 text-sm" value={fin} onChange={(e) => setFin(e.target.value)} />
        </div>
        <div>
          <label className="text-xs text-slate-500">{t("statut")}</label>
          <select className="flex h-8 w-full rounded border border-slate-200 px-1 text-sm" value={statut} onChange={(e) => setStatut(e.target.value)}>
            <option value="">{tc("all")}</option>
            {["BROUILLON", "SOUMISE", "APPROUVEE", "EN_COURS", "TERMINEE", "ANNULEE"].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
      </aside>

      <div className="min-w-0 flex-1 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">{t("title")}</h1>
            <p className="text-sm text-slate-600">{t("subtitle")}</p>
          </div>
          <div className="flex gap-2">
            {canAll ? (
              <div className="flex rounded-md border border-slate-200 bg-white p-1">
                <Button type="button" size="sm" variant={mode === "mine" ? "secondary" : "ghost"} onClick={() => setMode("mine")}>
                  {t("tabMine")}
                </Button>
                <Button type="button" size="sm" variant={mode === "all" ? "secondary" : "ghost"} onClick={() => setMode("all")}>
                  {t("tabAll")}
                </Button>
              </div>
            ) : null}

            <Link href="/missions/new" className={cn(buttonVariants({ variant: "default" }))}>
              + {t("create")}
            </Link>
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
                  <TableCell colSpan={columns.length}>{t("emptyList")}</TableCell>
                </TableRow>
              ) : (
                table.getRowModel().rows.map((row) => (
                  <TableRow key={row.id} className="cursor-pointer">
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                    ))}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>

        {listData && listData.totalPages > 1 ? (
          <div className="flex justify-between text-sm">
            <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
              {tc("previous")}
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={listData.last} onClick={() => setPage((p) => p + 1)}>
              {tc("next")}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  );
}

