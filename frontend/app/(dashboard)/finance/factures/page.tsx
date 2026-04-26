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
import { toast } from "sonner";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { FactureModal } from "@/components/finance/FactureModal";
import type { FactureRequest, FactureResponse } from "@/lib/types/finance";
import { useAuthStore } from "@/lib/store";
import { createFacture, listCategories, listFactures } from "@/services/finance.service";

function statutBadge(s: string): "muted" | "warning" | "success" | "dangerSolid" {
  if (s === "BROUILLON") return "muted";
  if (s === "A_PAYER") return "warning";
  if (s === "PAYE") return "success";
  return "dangerSolid";
}

function fmt(v: string | number) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  return Number.isNaN(n) ? String(v) : n.toFixed(2);
}

function downloadCsv(rows: FactureResponse[], headerLine: string) {
  const lines = [
    headerLine,
    ...rows.map((r) =>
      [r.reference, r.fournisseur, r.dateFacture, fmt(r.montantHt), fmt(r.montantTtc), r.categorieLibelle ?? "", r.statut].join(
        ";"
      )
    ),
  ];
  const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `factures-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(a.href);
}

export default function FacturesPage() {
  const t = useTranslations("Finance.factures");
  const tc = useTranslations("Common");
  const qc = useQueryClient();
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const isFin = user?.role === "FINANCIER";
  const isAdmin = user?.role === "ADMIN";
  const canCreate = isFin || isAdmin;
  const [page, setPage] = useState(0);
  const [statut, setStatut] = useState("");
  const [categorieId, setCategorieId] = useState("");
  const [debut, setDebut] = useState("");
  const [fin, setFin] = useState("");
  const [fournisseur, setFournisseur] = useState("");
  const [createOpen, setCreateOpen] = useState(false);

  const { data: categories } = useQuery({ queryKey: ["finance", "categories"], queryFn: listCategories });
  const { data: listData, isLoading } = useQuery({
    queryKey: ["finance", "factures", page, statut, categorieId, debut, fin, fournisseur],
    queryFn: () =>
      listFactures({
        page,
        size: 20,
        statut: statut || undefined,
        categorieId: categorieId || undefined,
        debut: debut || undefined,
        fin: fin || undefined,
        fournisseur: fournisseur || undefined,
      }),
  });

  const mutCreate = useMutation({
    mutationFn: ({ req, file }: { req: FactureRequest; file: File | null }) => createFacture(req, file),
    onSuccess: () => {
      toast.success(tc("successCreated"));
      qc.invalidateQueries({ queryKey: ["finance", "factures"] });
    },
  });

  const rows = listData?.content ?? [];

  const columns = useMemo<ColumnDef<FactureResponse>[]>(
    () => [
      {
        accessorKey: "reference",
        header: t("thReference"),
        cell: ({ row }) => (
          <Link
            className="text-indigo-700 hover:underline"
            href={`/finance/factures/${row.original.id}`}
            onClick={(e) => e.stopPropagation()}
          >
            {row.original.reference}
          </Link>
        ),
      },
      { accessorKey: "fournisseur", header: t("thFournisseur") },
      { accessorKey: "dateFacture", header: t("thDate") },
      {
        id: "ht",
        header: t("thHt"),
        cell: ({ row }) => <span>{fmt(row.original.montantHt)}</span>,
      },
      {
        id: "ttc",
        header: t("thTtc"),
        cell: ({ row }) => (
          <span>
            {fmt(row.original.montantTtc)} {row.original.devise}
          </span>
        ),
      },
      {
        accessorKey: "categorieLibelle",
        header: t("thCategorie"),
        cell: ({ row }) => row.original.categorieLibelle ?? tc("emDash"),
      },
      {
        accessorKey: "statut",
        header: t("thStatut"),
        cell: ({ row }) => <Badge variant={statutBadge(row.original.statut)}>{row.original.statut}</Badge>,
      },
      {
        id: "open",
        header: "",
        cell: ({ row }) => (
          <Link
            href={`/finance/factures/${row.original.id}`}
            onClick={(e) => e.stopPropagation()}
            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
          >
            {t("detailLink")}
          </Link>
        ),
      },
    ],
    [t, tc]
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="flex gap-6">
      <aside className="hidden w-56 shrink-0 space-y-3 rounded-lg border border-border bg-card p-3 text-card-foreground lg:block">
        <p className="text-xs font-semibold uppercase text-muted-foreground">{t("filters")}</p>
        <div>
          <label className="text-xs text-muted-foreground">{t("debut")}</label>
          <Input type="date" className="h-8 text-sm" value={debut} onChange={(e) => setDebut(e.target.value)} />
        </div>
        <div>
          <label className="text-xs text-muted-foreground">{t("fin")}</label>
          <Input type="date" className="h-8 text-sm" value={fin} onChange={(e) => setFin(e.target.value)} />
        </div>
        <div>
          <label className="text-xs text-muted-foreground">{t("categorie")}</label>
          <select
            className="flex h-8 w-full rounded border border-border bg-background px-1 text-sm text-foreground"
            value={categorieId}
            onChange={(e) => setCategorieId(e.target.value)}
          >
            <option value="">{tc("allFeminine")}</option>
            {(categories ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.libelle}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="text-xs text-muted-foreground">{t("statut")}</label>
          <select
            className="flex h-8 w-full rounded border border-border bg-background px-1 text-sm text-foreground"
            value={statut}
            onChange={(e) => setStatut(e.target.value)}
          >
            <option value="">{tc("all")}</option>
            <option value="BROUILLON">BROUILLON</option>
            <option value="A_PAYER">A_PAYER</option>
            <option value="PAYE">PAYE</option>
            <option value="ANNULE">ANNULE</option>
          </select>
        </div>
        <div>
          <label className="text-xs text-muted-foreground">{t("fournisseur")}</label>
          <Input className="h-8 text-sm" value={fournisseur} onChange={(e) => setFournisseur(e.target.value)} />
        </div>
      </aside>

      <div className="min-w-0 flex-1 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
            <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
          </div>
          <div className="flex gap-2">
            {canCreate && (
              <Button type="button" onClick={() => setCreateOpen(true)}>
                + {t("create")}
              </Button>
            )}
            <Button type="button" variant="outline" onClick={() => downloadCsv(rows, t("csvHeader"))}>
              {t("exportCsv")}
            </Button>
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
                  <TableCell colSpan={columns.length}>{t("emptyList")}</TableCell>
                </TableRow>
              ) : (
                table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    className="cursor-pointer"
                    onClick={() => router.push(`/finance/factures/${row.original.id}`)}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                    ))}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>

        {listData && listData.totalPages > 1 && (
          <div className="flex justify-between text-sm">
            <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
              {tc("previous")}
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={listData.last} onClick={() => setPage((p) => p + 1)}>
              {tc("next")}
            </Button>
          </div>
        )}
      </div>

      <FactureModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        initialFacture={null}
        categories={categories ?? []}
        onSubmit={async (req, file) => {
          await mutCreate.mutateAsync({ req, file });
        }}
      />
    </div>
  );
}
