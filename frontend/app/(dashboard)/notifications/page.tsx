"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { normalizeNotificationLink } from "@/lib/notification-link";
import { listMyNotifications, markAllNotificationsRead, markNotificationRead } from "@/services/notifications.service";

export default function NotificationsPage() {
  const t = useTranslations("Notifications");
  const tc = useTranslations("Common");
  const [nonLuesSeulement, setNonLuesSeulement] = useState(false);
  const [page, setPage] = useState(0);

  const q = useQuery({
    queryKey: ["notifications", { nonLuesSeulement, page }],
    queryFn: () => listMyNotifications({ nonLuesSeulement, page, size: 20 }),
  });

  const rows = q.data?.content ?? [];
  const totalPages = q.data?.totalPages ?? 0;

  const pageLabel = useMemo(() => `${page + 1}/${Math.max(1, totalPages)}`, [page, totalPages]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant={nonLuesSeulement ? "secondary" : "outline"} onClick={() => setNonLuesSeulement((v) => !v)}>
            {nonLuesSeulement ? t("filterUnread") : t("filterAll")}
          </Button>
          <Button type="button" variant="secondary" onClick={() => markAllNotificationsRead().then(() => q.refetch()).catch(() => {})}>
            {t("markAllRead")}
          </Button>
        </div>
      </div>

      <div className="rounded-lg border border-border bg-card text-card-foreground">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("thType")}</TableHead>
              <TableHead>{t("thTitle")}</TableHead>
              <TableHead>{t("thMessage")}</TableHead>
              <TableHead>{t("thDate")}</TableHead>
              <TableHead>{t("thStatus")}</TableHead>
              <TableHead className="text-right">{tc("actions")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {q.isLoading ? (
              <TableRow>
                <TableCell colSpan={6}>{tc("loading")}</TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6}>{t("empty")}</TableCell>
              </TableRow>
            ) : (
              rows.map((n) => (
                <TableRow key={n.id}>
                  <TableCell className="text-xs">{n.type}</TableCell>
                  <TableCell className="font-medium">{n.titre}</TableCell>
                  <TableCell className="max-w-[30rem] truncate">{n.message}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{n.createdAt ?? tc("emDash")}</TableCell>
                  <TableCell>{n.lu ? t("statusRead") : t("statusUnread")}</TableCell>
                  <TableCell className="text-right">
                    <div className="inline-flex items-center justify-end gap-2">
                      {n.lien ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            const href = normalizeNotificationLink(n.lien);
                            if (href) window.location.href = href;
                          }}
                        >
                          {tc("open")}
                        </Button>
                      ) : null}
                      {!n.lu ? (
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          onClick={() => markNotificationRead(n.id).then(() => q.refetch()).catch(() => {})}
                        >
                          {t("markRead")}
                        </Button>
                      ) : null}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">{pageLabel}</div>
        <div className="flex gap-2">
          <Button type="button" variant="outline" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
            {tc("previous")}
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={totalPages === 0 || page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            {tc("next")}
          </Button>
        </div>
      </div>
    </div>
  );
}

