"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuthStore } from "@/lib/store";
import { listAuditLogs } from "@/services/admin.service";

function JsonCell({ value }: { value: unknown }) {
  if (value == null) return <span className="text-muted-foreground">—</span>;
  const s = JSON.stringify(value, null, 0);
  const short = s.length > 120 ? `${s.slice(0, 120)}…` : s;
  return (
    <pre className="max-w-[min(28rem,40vw)] overflow-x-auto whitespace-pre-wrap break-all text-xs text-muted-foreground" title={s}>
      {short}
    </pre>
  );
}

export default function AdminAuditPage() {
  const t = useTranslations("Admin.audit");
  const tc = useTranslations("Common");
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
        {t("restricted")}
      </div>
    );
  }

  const rows = data?.content ?? [];

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <div className="overflow-x-auto rounded-lg border border-border bg-card text-card-foreground">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="whitespace-nowrap">{t("thDateUtc")}</TableHead>
              <TableHead>{t("thAction")}</TableHead>
              <TableHead>{t("thEntite")}</TableHead>
              <TableHead>{t("thUtilisateur")}</TableHead>
              <TableHead className="whitespace-nowrap">{t("thIp")}</TableHead>
              <TableHead>{t("thUserAgent")}</TableHead>
              <TableHead>{t("thAvant")}</TableHead>
              <TableHead>{t("thApres")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={8}>{tc("loading")}</TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8}>{t("empty")}</TableCell>
              </TableRow>
            ) : (
              rows.map((log) => (
                <TableRow key={log.id}>
                  <TableCell className="whitespace-nowrap align-top text-xs">{log.dateAction}</TableCell>
                  <TableCell className="align-top text-sm">{log.action}</TableCell>
                  <TableCell className="align-top text-sm">
                    {log.entite}
                    {log.entiteId ? (
                      <span className="block text-xs text-muted-foreground">{log.entiteId}</span>
                    ) : null}
                  </TableCell>
                  <TableCell className="align-top text-xs">{log.utilisateurEmail ?? log.utilisateurId ?? tc("emDash")}</TableCell>
                  <TableCell className="align-top text-xs text-muted-foreground">{log.ipAddress ?? tc("emDash")}</TableCell>
                  <TableCell className="align-top max-w-[12rem] truncate text-xs text-muted-foreground" title={log.userAgent ?? undefined}>
                    {log.userAgent ?? tc("emDash")}
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
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>
            {tc("page", { current: data.page + 1, total: data.totalPages })}
          </span>
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" disabled={data.page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              {tc("previous")}
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              {tc("next")}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
