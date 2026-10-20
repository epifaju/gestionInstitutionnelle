"use client";

import { useCallback, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import { useLocale, useTranslations } from "next-intl";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { intlLocaleTag } from "@/lib/intl-locale";
import { useAuthStore } from "@/lib/store";
import { cn } from "@/lib/utils";
import type { MarquerPayeRequest, PaieResponse } from "@/lib/types/rh";
import {
  annulerPaie,
  getPaieAnnuelle,
  getPayslipPresignedUrlForSalarie,
  marquerPaye,
} from "@/services/paie.service";
import { getSalarie } from "@/services/salarie.service";

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

export default function PaieSalarieAnneePage() {
  const params = useParams<{ salarieId: string; annee: string }>();
  const salarieId = params.salarieId;
  const anneeParam = Number(params.annee);
  const anneeInit = Number.isFinite(anneeParam) && anneeParam > 2000 ? anneeParam : new Date().getFullYear();

  const t = useTranslations("RH.paie");
  const tc = useTranslations("Common");
  const localeTag = intlLocaleTag(useLocale());
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const canMark = user?.role === "RH" || user?.role === "FINANCIER" || user?.role === "ADMIN";
  const canLoadSalarieFiche = user?.role === "RH" || user?.role === "ADMIN";

  const [annee, setAnnee] = useState(anneeInit);
  const [page, setPage] = useState(0);
  const [markOpen, setMarkOpen] = useState(false);
  const [markConfirmOpen, setMarkConfirmOpen] = useState(false);
  const [markTarget, setMarkTarget] = useState<PaieResponse | null>(null);
  const [markForm, setMarkForm] = useState<MarquerPayeRequest>({
    datePaiement: new Date().toISOString().slice(0, 10),
    modePaiement: "VIREMENT",
    notes: "",
  });
  const [cancelId, setCancelId] = useState<string | null>(null);
  const [downloading, setDownloading] = useState<string | null>(null);

  const { data: salarie, isLoading: salarieLoading } = useQuery({
    queryKey: ["rh", "salaries", salarieId],
    queryFn: () => getSalarie(salarieId),
    // getSalarie est RH/ADMIN (ou self) — FINANCIER s’appuie sur PaieResponse.
    enabled: !!salarieId && canLoadSalarieFiche,
  });

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["rh", "paie-salarie", salarieId, annee, page],
    queryFn: () => getPaieAnnuelle(salarieId, annee, { page, size: 12 }),
    enabled: !!salarieId && Number.isFinite(annee),
  });

  const rows = data?.content ?? [];
  const headerName =
    salarie != null
      ? `${salarie.prenom ?? ""} ${salarie.nom ?? ""}`.trim() || rows[0]?.salarieNomComplet
      : rows[0]?.salarieNomComplet;
  const matricule = salarie?.matricule ?? rows[0]?.matricule;

  const invalidatePaie = useCallback(() => {
    qc.invalidateQueries({ queryKey: ["rh", "paie-salarie", salarieId] });
    qc.invalidateQueries({ queryKey: ["rh", "paie-org"] });
    qc.invalidateQueries({ queryKey: ["todo-dashboard"] });
    qc.invalidateQueries({ queryKey: ["sidebar-comptages"] });
  }, [qc, salarieId]);

  const mutMarkPaid = useMutation({
    mutationFn: ({ id, body }: { id: string; body: MarquerPayeRequest }) => marquerPaye(id, body),
    onSuccess: () => {
      toast.success(t("markPaidSuccess"));
      invalidatePaie();
      setMarkOpen(false);
      setMarkConfirmOpen(false);
      setMarkTarget(null);
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  const mutCancel = useMutation({
    mutationFn: (id: string) => annulerPaie(id),
    onSuccess: () => {
      toast.success(t("cancelPaymentSuccess"));
      invalidatePaie();
      setCancelId(null);
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  function openMarkPaid(p: PaieResponse) {
    setMarkTarget(p);
    setMarkForm({
      datePaiement: new Date().toISOString().slice(0, 10),
      modePaiement: "VIREMENT",
      notes: "",
    });
    setMarkOpen(true);
    setMarkConfirmOpen(false);
  }

  const downloadPayslip = useCallback(
    async (row: PaieResponse) => {
      if (!row.hasPayslip) return;
      setDownloading(row.id);
      const w = window.open("about:blank", "_blank");
      if (!w) {
        toast.error(tc("popupBlocked"));
        setDownloading(null);
        return;
      }
      try {
        w.opener = null;
      } catch {
        /* ignore */
      }
      try {
        const res = await getPayslipPresignedUrlForSalarie(row.salarieId, row.annee, row.mois);
        const url = res?.url;
        if (!url) {
          toast.error(tc("errorGeneric"));
          try {
            w.close();
          } catch {
            /* ignore */
          }
          return;
        }
        w.location.href = url;
      } catch {
        toast.error(tc("errorGeneric"));
        try {
          w.close();
        } catch {
          /* ignore */
        }
      } finally {
        setDownloading(null);
      }
    },
    [tc]
  );

  const columns = useMemo<ColumnDef<PaieResponse>[]>(
    () => [
      {
        accessorKey: "mois",
        header: t("thPeriode"),
        cell: ({ row }) => (
          <span>
            {row.original.mois}/{row.original.annee}
          </span>
        ),
      },
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
        header: () => <div className="text-right">{t("thActions")}</div>,
        cell: ({ row }) => {
          const p = row.original;
          const showMarkCancel = canMark && p.statut === "EN_ATTENTE";
          return (
            <div className="text-right">
              <div className="inline-flex flex-wrap items-center justify-end gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={!p.hasPayslip || downloading === p.id}
                  onClick={() => downloadPayslip(p)}
                >
                  {downloading === p.id ? tc("loading") : t("downloadPayslip")}
                </Button>
                {showMarkCancel ? (
                  <>
                    <Button type="button" size="sm" variant="secondary" onClick={() => openMarkPaid(p)}>
                      {tc("markPaid")}
                    </Button>
                    <Button type="button" size="sm" variant="outline" onClick={() => setCancelId(p.id)}>
                      {tc("cancel")}
                    </Button>
                  </>
                ) : null}
              </div>
            </div>
          );
        },
      },
    ],
    [canMark, downloadPayslip, downloading, localeTag, t, tc]
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          <Link href="/rh/paie" className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "px-0")}>
            {t("backToList")}
          </Link>
          {salarieLoading && !headerName ? (
            <Skeleton className="h-8 w-64" />
          ) : (
            <h1 className="text-2xl font-semibold text-foreground">{t("detailTitle")}</h1>
          )}
          <p className="text-sm text-muted-foreground">
            {headerName ?? tc("loading")}
            {matricule ? ` · ${matricule}` : ""}
            {salarie?.service ? ` · ${salarie.service}` : ""}
          </p>
          <p className="text-xs text-muted-foreground">{t("detailSubtitle", { annee })}</p>
        </div>

        <div className="flex items-center gap-2">
          <label className="text-sm text-muted-foreground" htmlFor="annee-paie-detail">
            {tc("year")}
          </label>
          <Input
            id="annee-paie-detail"
            type="number"
            className="w-28"
            value={annee}
            onChange={(e) => {
              setPage(0);
              setAnnee(Number(e.target.value) || new Date().getFullYear());
            }}
          />
          <Button type="button" variant="outline" size="sm" onClick={() => refetch()}>
            {tc("refresh")}
          </Button>
        </div>
      </div>

      {isError ? (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          {tc("errorGeneric")}{" "}
          <Button type="button" variant="link" className="h-auto p-0" onClick={() => refetch()}>
            {tc("retry")}
          </Button>
        </div>
      ) : (
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
      )}

      {data && data.totalPages > 1 ? (
        <div className="flex justify-between text-sm">
          <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            {tc("previous")}
          </Button>
          <Button type="button" variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            {tc("next")}
          </Button>
        </div>
      ) : null}

      {markOpen && markTarget ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-card p-4 text-card-foreground shadow-xl">
            <h3 className="mb-1 font-semibold">{t("markPaidTitle")}</h3>
            <p className="mb-3 text-xs text-muted-foreground">
              {markTarget.salarieNomComplet} · {markTarget.mois}/{markTarget.annee} ·{" "}
              {fmtMoney(markTarget.montant, markTarget.devise, localeTag)}
            </p>
            <div className="space-y-2">
              <div>
                <Label>{t("labelDatePaiement")}</Label>
                <Input
                  type="date"
                  value={markForm.datePaiement}
                  onChange={(e) => setMarkForm((f) => ({ ...f, datePaiement: e.target.value }))}
                />
              </div>
              <div>
                <Label>{t("labelMode")}</Label>
                <Input value={markForm.modePaiement} onChange={(e) => setMarkForm((f) => ({ ...f, modePaiement: e.target.value }))} />
              </div>
              <div>
                <Label>{t("labelNotes")}</Label>
                <Input value={markForm.notes ?? ""} onChange={(e) => setMarkForm((f) => ({ ...f, notes: e.target.value }))} />
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setMarkOpen(false)}>
                {tc("cancel")}
              </Button>
              <Button type="button" disabled={mutMarkPaid.isPending} onClick={() => setMarkConfirmOpen(true)}>
                {tc("confirm")}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {markConfirmOpen && markTarget ? (
        <div className="fixed inset-0 z-[55] flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-card p-4 text-card-foreground shadow-xl">
            <h3 className="mb-1 font-semibold">{t("markPaidConfirmTitle")}</h3>
            <p className="mb-3 text-sm text-muted-foreground">{t("markPaidConfirmHint")}</p>
            <div className="rounded-md bg-muted p-3 text-xs text-foreground">
              <div className="font-medium">{markTarget.salarieNomComplet}</div>
              <div>
                {markTarget.mois}/{markTarget.annee} · {fmtMoney(markTarget.montant, markTarget.devise, localeTag)}
              </div>
              <div className="mt-2 grid gap-1">
                <div>
                  <span className="text-muted-foreground">{t("labelDatePaiement")}:</span> {markForm.datePaiement}
                </div>
                <div>
                  <span className="text-muted-foreground">{t("labelMode")}:</span> {markForm.modePaiement}
                </div>
                {markForm.notes ? (
                  <div>
                    <span className="text-muted-foreground">{t("labelNotes")}:</span> {markForm.notes}
                  </div>
                ) : null}
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button type="button" variant="outline" disabled={mutMarkPaid.isPending} onClick={() => setMarkConfirmOpen(false)}>
                {tc("back")}
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={mutMarkPaid.isPending}
                onClick={() => mutMarkPaid.mutate({ id: markTarget.id, body: markForm })}
              >
                {t("confirmMarkPaid")}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {cancelId ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-card p-4 text-card-foreground shadow-xl">
            <h3 className="mb-1 font-semibold">{t("cancelPaymentTitle")}</h3>
            <p className="mb-3 text-xs text-muted-foreground">{t("cancelPaymentHint")}</p>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setCancelId(null)}>
                {tc("back")}
              </Button>
              <Button type="button" variant="destructive" disabled={mutCancel.isPending} onClick={() => mutCancel.mutate(cancelId)}>
                {t("confirmCancelPayment")}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
