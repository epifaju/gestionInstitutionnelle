"use client";

import { useCallback, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { intlLocaleTag } from "@/lib/intl-locale";
import type { ColumnDef } from "@tanstack/react-table";
import { flexRender, getCoreRowModel, useReactTable } from "@tanstack/react-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuthStore } from "@/lib/store";
import type { PaieResponse } from "@/lib/types/rh";
import { getMyPayslipPresignedUrl, listMyPaie } from "@/services/paie.service";
import { toast } from "sonner";

function fmtMoney(v: string | number, devise: string, localeTag: string) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return new Intl.NumberFormat(localeTag, { style: "currency", currency: devise || "EUR" }).format(n);
}

function paieVariant(s: string): "success" | "warning" | "default" {
  if (s === "PAYE") return "success";
  if (s === "EN_ATTENTE") return "warning";
  return "default";
}

export default function MyPaiePage() {
  const t = useTranslations("RH.myPaie");
  const tc = useTranslations("Common");
  const localeTag = intlLocaleTag(useLocale());
  const user = useAuthStore((s) => s.user);
  const [annee, setAnnee] = useState(() => new Date().getFullYear());
  const [page, setPage] = useState(0);
  const [downloading, setDownloading] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["rh", "my-paie", annee, page],
    queryFn: () => listMyPaie(annee, { page, size: 12 }),
  });

  const rows = data?.content ?? [];

  const downloadPayslip = useCallback(async (row: PaieResponse) => {
    if (!row.hasPayslip) return;
    setDownloading(row.id);
    // Open the tab synchronously (otherwise browsers may block it after async awaits)
    const w = window.open("about:blank", "_blank");
    if (!w) {
      toast.error(tc("popupBlocked"));
      setDownloading(null);
      return;
    }
    // Security hardening: avoid giving the new tab access to this window.
    try { w.opener = null; } catch {}
    try {
      const res = await getMyPayslipPresignedUrl(row.annee, row.mois);
      const url = res?.url;
      if (!url) {
        toast.error(tc("errorGeneric"));
        try { w.close(); } catch {}
        return;
      }
      w.location.href = url;
    } catch {
      toast.error(tc("errorGeneric"));
      try { w.close(); } catch {}
    } finally {
      setDownloading(null);
    }
  }, [tc]);

  const columns = useMemo<ColumnDef<PaieResponse>[]>(
    () => [
      { accessorKey: "mois", header: t("thMois") },
      {
        id: "montant",
        header: t("thMontant"),
        cell: ({ row }) => <span>{fmtMoney(row.original.montant, row.original.devise, localeTag)}</span>,
      },
      {
        accessorKey: "statut",
        header: t("thStatut"),
        cell: ({ row }) => <Badge variant={paieVariant(row.original.statut)}>{row.original.statut}</Badge>,
      },
      {
        accessorKey: "datePaiement",
        header: t("thDatePaiement"),
        cell: ({ row }) => row.original.datePaiement ?? tc("emDash"),
      },
      {
        accessorKey: "modePaiement",
        header: t("thMode"),
        cell: ({ row }) => row.original.modePaiement ?? tc("emDash"),
      },
      {
        id: "actions",
        header: () => <div className="text-right">{tc("actions")}</div>,
        cell: ({ row }) => {
          const r = row.original;
          return (
            <div className="text-right">
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={!r.hasPayslip || downloading === r.id}
                onClick={() => downloadPayslip(r)}
              >
                {downloading === r.id ? tc("loading") : t("downloadPayslip")}
              </Button>
            </div>
          );
        },
      },
    ],
    [downloading, downloadPayslip, localeTag, t, tc]
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
          <p className="text-xs text-muted-foreground">
            {user?.prenom ?? ""} {user?.nom ?? ""}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-sm text-muted-foreground" htmlFor="annee-my-paie">
            {tc("year")}
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

