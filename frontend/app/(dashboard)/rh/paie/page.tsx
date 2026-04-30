"use client";

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import { useCallback, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { intlLocaleTag } from "@/lib/intl-locale";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import type { PaieResponse } from "@/lib/types/rh";
import type { MarquerPayeRequest } from "@/lib/types/rh";
import type { SalarieResponse } from "@/lib/types/rh";
import { useAuthStore } from "@/lib/store";
import { annulerPaie, getPayslipPresignedUrlForSalarie, listPaieOrganisation, marquerPaye } from "@/services/paie.service";
import { listSalaries } from "@/services/salarie.service";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ExportButton } from "@/components/exports/ExportButton";
import { exportEtatPaieExcel, exportEtatPaiePdf } from "@/services/export-conformite.service";

function fmtMoney(v: string | number, devise: string, localeTag: string) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return new Intl.NumberFormat(localeTag, { style: "currency", currency: devise || "EUR" }).format(n);
}

export default function PaieOrganisationPage() {
  const t = useTranslations("RH.paie");
  const tc = useTranslations("Common");
  const localeTag = intlLocaleTag(useLocale());
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const canMark = user?.role === "RH" || user?.role === "FINANCIER" || user?.role === "ADMIN";
  const [annee, setAnnee] = useState(() => new Date().getFullYear());
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
  const [exportOpen, setExportOpen] = useState(false);
  const [exportMois, setExportMois] = useState(() => {
    const d = new Date();
    const m = d.getMonth(); // previous month (0..11), 0 means January -> use 12
    return m === 0 ? 12 : m;
  });
  const [exportService, setExportService] = useState<string>("__ALL__");

  const { data: servicesData } = useQuery({
    queryKey: ["rh", "salaries", "services", "paie-export-dialog"],
    queryFn: async () => {
      const page = await listSalaries({ page: 0, size: 200 });
      const services = Array.from(new Set((page.content as SalarieResponse[]).map((s) => s.service).filter(Boolean)));
      return services.sort((a, b) => a.localeCompare(b));
    },
    staleTime: 60_000,
    enabled: !!user && canMark,
  });

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
      setMarkConfirmOpen(false);
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
    setMarkConfirmOpen(false);
  }

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
      const res = await getPayslipPresignedUrlForSalarie(row.salarieId, row.annee, row.mois);
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
      { accessorKey: "salarieNomComplet", header: t("thSalarie") },
      { accessorKey: "matricule", header: t("thMatricule") },
      {
        id: "periode",
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
      { accessorKey: "statut", header: t("thStatut") },
      ...(canMark
        ? ([
            {
              id: "actions",
              header: () => <div className="text-right">{t("thActions")}</div>,
              cell: ({ row }) => {
                const p = row.original;
                const showMarkCancel = p.statut === "EN_ATTENTE";
                return (
                  <div className="text-right">
                    <div className="inline-flex items-center justify-end gap-2">
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
          ] as ColumnDef<PaieResponse>[])
        : []),
    ],
    [canMark, downloadPayslip, downloading, localeTag, t, tc]
  );

  const table = useReactTable({ data: rows, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" onClick={() => setExportOpen(true)}>
            📄 État de paie PDF
          </Button>
          <Button type="button" variant="outline" onClick={() => setExportOpen(true)}>
            📊 État de paie Excel
          </Button>
          <label className="text-sm text-muted-foreground" htmlFor="annee-paie">
            {tc("year")}
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

      <Dialog open={exportOpen} onOpenChange={setExportOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Exports — État de paie</DialogTitle>
            <DialogDescription>Sélectionnez la période et le service.</DialogDescription>
          </DialogHeader>

          <div className="grid gap-3 md:grid-cols-3">
            <div className="space-y-1">
              <Label>Année</Label>
              <Input type="number" value={annee} onChange={(e) => setAnnee(Number(e.target.value) || new Date().getFullYear())} />
            </div>
            <div className="space-y-1">
              <Label>Mois</Label>
              <Input type="number" min={1} max={12} value={exportMois} onChange={(e) => setExportMois(Number(e.target.value) || 1)} />
            </div>
            <div className="space-y-1">
              <Label>Service</Label>
              <select
                className="h-10 w-full rounded-md border bg-background px-3 text-sm"
                value={exportService}
                onChange={(e) => setExportService(e.target.value)}
              >
                <option value="__ALL__">Tous les services</option>
                {(servicesData ?? []).map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            <ExportButton
              label="Exporter PDF"
              variant="pdf"
              onExport={() =>
                exportEtatPaiePdf({ annee, mois: exportMois, service: exportService === "__ALL__" ? null : exportService })
              }
            />
            <ExportButton
              label="Exporter Excel"
              variant="excel"
              onExport={() =>
                exportEtatPaieExcel({ annee, mois: exportMois, service: exportService === "__ALL__" ? null : exportService })
              }
            />
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setExportOpen(false)}>
              {tc("close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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

      {markOpen && markTarget && (
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
              <Button
                type="button"
                disabled={mutMarkPaid.isPending}
                onClick={() => setMarkConfirmOpen(true)}
              >
                {tc("confirm")}
              </Button>
            </div>
          </div>
        </div>
      )}

      {markConfirmOpen && markTarget && (
        <div className="fixed inset-0 z-[55] flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-card p-4 text-card-foreground shadow-xl">
            <h3 className="mb-1 font-semibold">{t("markPaidConfirmTitle")}</h3>
            <p className="mb-3 text-sm text-muted-foreground">{t("markPaidConfirmHint")}</p>
            <div className="rounded-md bg-muted p-3 text-xs text-foreground">
              <div className="font-medium text-foreground">{markTarget.salarieNomComplet}</div>
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
      )}

      {cancelId && (
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
      )}
    </div>
  );
}
