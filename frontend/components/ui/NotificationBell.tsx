"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Bell } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { useNotifications } from "@/hooks/useNotifications";
import { normalizeNotificationLink } from "@/lib/notification-link";

function formatRelative(iso: string | null, locale: string) {
  if (!iso) return "";
  const d = new Date(iso);
  const diffSec = Math.round((d.getTime() - Date.now()) / 1000);
  const abs = Math.abs(diffSec);
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
  if (abs < 60) return rtf.format(diffSec, "second");
  const diffMin = Math.round(diffSec / 60);
  if (Math.abs(diffMin) < 60) return rtf.format(diffMin, "minute");
  const diffHr = Math.round(diffMin / 60);
  if (Math.abs(diffHr) < 24) return rtf.format(diffHr, "hour");
  const diffDay = Math.round(diffHr / 24);
  return rtf.format(diffDay, "day");
}

export function NotificationBell() {
  const t = useTranslations("Notifications");
  const locale = useLocale();
  const { items, unreadCount, markAllRead, markAsRead } = useNotifications();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!open) return;
      const el = rootRef.current;
      if (!el) return;
      if (e.target instanceof Node && !el.contains(e.target)) setOpen(false);
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <Button type="button" variant="ghost" size="icon" aria-label={t("title")} onClick={() => setOpen((v) => !v)}>
        <span className="relative">
          <Bell className="h-5 w-5" />
          {unreadCount > 0 ? (
            <span className="absolute -right-2 -top-2 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-600 px-1 text-xs font-semibold text-white">
              {unreadCount > 99 ? "99+" : unreadCount}
            </span>
          ) : null}
        </span>
      </Button>

      {open ? (
        <div className="absolute right-0 z-50 mt-2 w-[22rem] rounded-lg border border-slate-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2">
            <p className="text-sm font-semibold text-slate-900">{t("title")}</p>
            <div className="flex items-center gap-2">
              <Button type="button" variant="ghost" size="sm" onClick={() => markAllRead().catch(() => {})}>
                {t("markAllReadShort")}
              </Button>
              <Link className="text-xs text-indigo-600 hover:underline" href="/notifications" onClick={() => setOpen(false)}>
                {t("seeAll")}
              </Link>
            </div>
          </div>
          <div className="max-h-[22rem] overflow-y-auto p-2">
            {items.length === 0 ? (
              <p className="p-3 text-sm text-slate-600">{t("empty")}</p>
            ) : (
              items.slice(0, 10).map((n) => (
                <button
                  key={n.id}
                  type="button"
                  className={`w-full rounded-md px-3 py-2 text-left hover:bg-slate-50 ${n.lu ? "opacity-80" : ""}`}
                  onClick={() => {
                    if (!n.lu) markAsRead(n.id).catch(() => {});
                    const href = normalizeNotificationLink(n.lien);
                    if (href) window.location.href = href;
                    setOpen(false);
                  }}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-slate-900">{n.titre}</p>
                      <p className="mt-0.5 line-clamp-2 text-xs text-slate-600">{n.message}</p>
                    </div>
                    <div className="shrink-0 text-[11px] text-slate-400">{formatRelative(n.createdAt, locale)}</div>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}

