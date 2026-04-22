"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuthStore } from "@/lib/store";
import { listAuditLogs } from "@/services/admin.service";

function JsonCell({ value }: { value: unknown }) {
  if (value == null) return <span className="text-slate-400">—</span>;
  const s = JSON.stringify(value, null, 0);
  const short = s.length > 120 ? `${s.slice(0, 120)}…` : s;
  return (
    <pre className="max-w-[min(28rem,40vw)] overflow-x-auto whitespace-pre-wrap break-all text-xs text-slate-700" title={s}>
      {short}
    </pre>
  );
}

export default function AdminAuditPage() {
  const user = useAuthStore((s) => s.user);
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "audit", page],
    queryFn: () => listAuditLogs({ page, size: 25 }),
    enabled: user?.role === "ADMIN",
  });

  if (user?.role !== "ADMIN") {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        Accès réservé aux administrateurs.
      </div>
    );
  }

  const rows = data?.content ?? [];

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Journal d’audit</h1>
        <p className="text-sm text-slate-600">
          Piste des actions sensibles (créations / mises à jour métier) pour la conformité et le contrôle interne.
        </p>
      </div>

      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="whitespace-nowrap">Date (UTC)</TableHead>
              <TableHead>Action</TableHead>
              <TableHead>Entité</TableHead>
              <TableHead>Utilisateur</TableHead>
              <TableHead className="whitespace-nowrap">IP</TableHead>
              <TableHead>User-Agent</TableHead>
              <TableHead>Avant</TableHead>
              <TableHead>Après</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={8}>Chargement…</TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8}>Aucune entrée</TableCell>
              </TableRow>
            ) : (
              rows.map((log) => (
                <TableRow key={log.id}>
                  <TableCell className="whitespace-nowrap align-top text-xs">{log.dateAction}</TableCell>
                  <TableCell className="align-top text-sm">{log.action}</TableCell>
                  <TableCell className="align-top text-sm">
                    {log.entite}
                    {log.entiteId ? (
                      <span className="block text-xs text-slate-500">{log.entiteId}</span>
                    ) : null}
                  </TableCell>
                  <TableCell className="align-top text-xs">{log.utilisateurEmail ?? log.utilisateurId ?? "—"}</TableCell>
                  <TableCell className="align-top text-xs text-slate-600">{log.ipAddress ?? "—"}</TableCell>
                  <TableCell className="align-top max-w-[12rem] truncate text-xs text-slate-600" title={log.userAgent ?? undefined}>
                    {log.userAgent ?? "—"}
                  </TableCell>
                  <TableCell className="align-top">
                    <JsonCell value={log.avant} />
                  </TableCell>
                  <TableCell className="align-top">
                    <JsonCell value={log.apres} />
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-600">
          <span>
            Page {data.page + 1} / {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" disabled={data.page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Précédent
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Suivant
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
