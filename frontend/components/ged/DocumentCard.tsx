"use client";

import type { DocumentResponse } from "@/lib/types/ged";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  FileImage,
  FileSpreadsheet,
  FileText,
  FileType2,
  MoreVertical,
  Download,
  Trash2,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";

function iconForMime(mime: string) {
  const m = (mime ?? "").toLowerCase();
  if (m === "application/pdf") return { Icon: FileText, className: "text-rose-600" };
  if (m.startsWith("image/")) return { Icon: FileImage, className: "text-slate-600" };
  if (m.includes("spreadsheetml") || m.includes("excel")) return { Icon: FileSpreadsheet, className: "text-emerald-600" };
  if (m.includes("wordprocessingml") || m.includes("word")) return { Icon: FileType2, className: "text-sky-600" };
  return { Icon: FileText, className: "text-slate-600" };
}

function daysUntil(dateIso: string | null) {
  if (!dateIso) return null;
  const d = new Date(dateIso);
  if (Number.isNaN(d.getTime())) return null;
  const now = new Date();
  const diffMs = d.getTime() - now.getTime();
  return Math.ceil(diffMs / (1000 * 60 * 60 * 24));
}

export function DocumentCard({
  doc,
  onDownload,
  onDelete,
}: {
  doc: DocumentResponse;
  onDownload: () => void;
  onDelete?: () => void;
}) {
  const t = useTranslations("Documents.card");
  const { Icon, className } = iconForMime(doc.mimeType);
  const [menuOpen, setMenuOpen] = useState(false);

  const exp = useMemo(() => daysUntil(doc.dateExpiration), [doc.dateExpiration]);
  const expBadge =
    exp == null
      ? null
      : exp < 0
        ? { label: t("expired", { days: Math.abs(exp) }), variant: "dangerSolid" as const }
        : exp < 7
          ? { label: t("expiresIn", { days: exp }), variant: "dangerSolid" as const }
          : exp < 30
            ? { label: t("expiresIn", { days: exp }), variant: "warning" as const }
            : { label: t("expiresIn", { days: exp }), variant: "muted" as const };

  return (
    <div className="group relative rounded-lg border border-slate-200 bg-white p-3 shadow-sm transition hover:shadow">
      <div className="flex items-start justify-between gap-2">
        <div className="flex min-w-0 items-start gap-3">
          <div className={cn("mt-0.5 rounded-md bg-slate-50 p-2", className)}>
            <Icon className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <p className="truncate font-semibold text-slate-900">{doc.titre}</p>
            {doc.description ? <p className="line-clamp-2 text-sm text-slate-600">{doc.description}</p> : null}
            <p className="mt-1 text-xs text-slate-500">
              {doc.typeDocument} · v{doc.version} · {(doc.tailleOctets / 1024).toFixed(0)} Ko
            </p>
          </div>
        </div>

        <div className="relative">
          <Button type="button" variant="outline" size="icon" onClick={() => setMenuOpen((v) => !v)} aria-label="Menu">
            <MoreVertical className="h-4 w-4" />
          </Button>
          {menuOpen ? (
            <div className="absolute right-0 top-9 z-10 w-44 rounded-md border border-slate-200 bg-white p-1 shadow-lg">
              <button
                type="button"
                className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
                onClick={() => {
                  setMenuOpen(false);
                  onDownload();
                }}
              >
                <Download className="h-4 w-4" />
                {t("download")}
              </button>
              {onDelete ? (
                <button
                  type="button"
                  className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-sm text-rose-700 hover:bg-rose-50"
                  onClick={() => {
                    setMenuOpen(false);
                    onDelete();
                  }}
                >
                  <Trash2 className="h-4 w-4" />
                  {t("delete")}
                </button>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        {expBadge ? <Badge variant={expBadge.variant}>{expBadge.label}</Badge> : null}
        {(doc.tags ?? []).slice(0, 6).map((t) => (
          <Badge key={t} variant="secondary" className="text-xs">
            {t}
          </Badge>
        ))}
      </div>
    </div>
  );
}

