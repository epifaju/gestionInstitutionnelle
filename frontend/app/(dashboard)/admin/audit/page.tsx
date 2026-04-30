"use client";

import { useMemo, useState } from "react";
import { useAuthStore } from "@/lib/store";
import { useQuery } from "@tanstack/react-query";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { ExportButton } from "@/components/exports/ExportButton";
import { exportJournalAuditCsv, exportJournalAuditExcel, exportJournalAuditPdf } from "@/services/export-conformite.service";
import { countAuditLogs, listAuditLogs, type AuditLogResponse } from "@/services/audit.service";

function actionBadge(action: string) {
  if (action === "CREATE") return "success";
  if (action === "UPDATE") return "info";
  if (action === "DELETE") return "dangerSolid";
  if (action === "LOGIN") return "muted";
  if (action === "EXPORT") return "warning";
  return "secondary";
}

function diffSummary(avant: unknown, apres: unknown) {
  try {
    if (!avant && apres) return "Création";
    if (avant && !apres) return "Suppression";
    if (!avant || !apres) return "—";
    const b = typeof avant === "string" ? (JSON.parse(avant) as unknown) : avant;
    const a = typeof apres === "string" ? (JSON.parse(apres) as unknown) : apres;
    if (!b || !a || typeof b !== "object" || typeof a !== "object") return "—";
    const bb = b as Record<string, unknown>;
    const aa = a as Record<string, unknown>;
    const keys = Object.keys({ ...bb, ...aa });
    const changes: string[] = [];
    for (const k of keys) {
      const vb = bb[k];
      const va = aa[k];
      if (JSON.stringify(vb) !== JSON.stringify(va)) changes.push(`${k}: ${String(vb)} → ${String(va)}`);
      if (changes.length >= 2) break;
    }
    return changes.length ? changes.join(" | ") : "—";
  } catch {
    return "—";
  }
}

export default function AdminAuditPage() {
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === "ADMIN";

  const [page, setPage] = useState(0);
  const [dateDebut, setDateDebut] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
  });
  const [dateFin, setDateFin] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  });
  const [entite, setEntite] = useState<string>("");
  const [action, setAction] = useState<string>("");
  const [utilisateurId, setUtilisateurId] = useState<string>("");

  const filters = useMemo(
    () => ({
      dateDebut,
      dateFin,
      entite: entite || null,
      action: action || null,
      utilisateurId: utilisateurId || null,
    }),
    [dateDebut, dateFin, entite, action, utilisateurId]
  );

  const countQuery = useQuery({
    queryKey: ["audit", "count", filters],
    queryFn: () => countAuditLogs(filters),
    enabled: isAdmin,
    staleTime: 10_000,
  });

  const logsQuery = useQuery({
    queryKey: ["audit", "logs", page, filters],
    queryFn: () => listAuditLogs({ page, size: 20, ...filters }),
    enabled: isAdmin,
    staleTime: 10_000,
  });

  const logs = logsQuery.data?.content ?? [];

  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<AuditLogResponse | null>(null);

  if (!isAdmin) {
    return <p className="text-sm text-muted-foreground">Accès réservé ADMIN.</p>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Journal d’Audit</h1>
          <p className="text-sm text-muted-foreground">Filtrer, consulter, et exporter les logs.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ExportButton label="PDF" variant="pdf" onExport={() => exportJournalAuditPdf(filters)} />
          <ExportButton label="Excel" variant="excel" onExport={() => exportJournalAuditExcel(filters)} />
          <ExportButton label="CSV" variant="csv" onExport={() => exportJournalAuditCsv(filters)} />
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Filtres</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-5">
          <div className="space-y-1">
            <Label>Début</Label>
            <Input type="date" value={dateDebut} onChange={(e) => setDateDebut(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label>Fin</Label>
            <Input type="date" value={dateFin} onChange={(e) => setDateFin(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label>Entité</Label>
            <Input value={entite} onChange={(e) => setEntite(e.target.value)} placeholder="Facture, Salarie..." />
          </div>
          <div className="space-y-1">
            <Label>Action</Label>
            <Input value={action} onChange={(e) => setAction(e.target.value)} placeholder="CREATE, UPDATE..." />
          </div>
          <div className="space-y-1">
            <Label>UtilisateurId</Label>
            <Input value={utilisateurId} onChange={(e) => setUtilisateurId(e.target.value)} placeholder="UUID" />
          </div>
          <div className="md:col-span-5 flex flex-wrap items-center justify-between gap-2 pt-2">
            <p className="text-sm text-muted-foreground">
              {countQuery.isLoading ? "…" : `~${countQuery.data ?? 0} événements`}
            </p>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  const d = new Date();
                  const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
                  setDateDebut(today);
                  setDateFin(today);
                }}
              >
                Aujourd’hui
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  const d = new Date();
                  const first = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
                  const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
                  setDateDebut(first);
                  setDateFin(today);
                }}
              >
                Ce mois
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="rounded-lg border border-border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date/Heure</TableHead>
              <TableHead>Utilisateur</TableHead>
              <TableHead>Action</TableHead>
              <TableHead>Entité</TableHead>
              <TableHead>Résumé changement</TableHead>
              <TableHead>IP</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {logsQuery.isLoading ? (
              <TableRow>
                <TableCell colSpan={6}>Chargement…</TableCell>
              </TableRow>
            ) : logs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6}>Aucun log.</TableCell>
              </TableRow>
            ) : (
              logs.map((l) => (
                <TableRow
                  key={l.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => {
                    setSelected(l);
                    setOpen(true);
                  }}
                >
                  <TableCell>{l.dateAction ? new Date(l.dateAction).toLocaleString() : "—"}</TableCell>
                  <TableCell>
                    <div className="text-sm">{l.utilisateurEmail ?? "—"}</div>
                    <div className="text-xs text-muted-foreground">{l.utilisateurRole ?? ""}</div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={actionBadge(l.action)}>{l.action}</Badge>
                  </TableCell>
                  <TableCell>{l.entite}</TableCell>
                  <TableCell className="max-w-[420px] truncate">{diffSummary(l.avant, l.apres)}</TableCell>
                  <TableCell>{l.ipAddress ?? "—"}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {logsQuery.data && logsQuery.data.totalPages > 1 ? (
        <div className="flex justify-between">
          <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            Précédent
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={logsQuery.data.last}
            onClick={() => setPage((p) => p + 1)}
          >
            Suivant
          </Button>
        </div>
      ) : null}

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Détail</SheetTitle>
            <SheetDescription>
              {selected?.entite} — {selected?.action}
            </SheetDescription>
          </SheetHeader>
          {selected ? (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">{selected.dateAction ? new Date(selected.dateAction).toLocaleString() : ""}</p>
                  <p className="text-sm">{selected.utilisateurEmail ?? "—"}</p>
                </div>
                <Button type="button" variant="outline" onClick={() => window.print()}>
                  Imprimer
                </Button>
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <div>
                  <p className="mb-1 text-sm font-semibold">Avant</p>
                  <pre className="max-h-[60vh] overflow-auto rounded-md bg-muted p-3 text-xs">{JSON.stringify(selected.avant, null, 2)}</pre>
                </div>
                <div>
                  <p className="mb-1 text-sm font-semibold">Après</p>
                  <pre className="max-h-[60vh] overflow-auto rounded-md bg-muted p-3 text-xs">{JSON.stringify(selected.apres, null, 2)}</pre>
                </div>
              </div>
            </div>
          ) : null}
        </SheetContent>
      </Sheet>
    </div>
  );
}

