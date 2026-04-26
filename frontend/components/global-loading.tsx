"use client";

import { useIsFetching } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";

export function GlobalLoading() {
  const t = useTranslations("Common");
  const fetching = useIsFetching({ fetchStatus: "fetching" });
  if (fetching === 0) return null;

  return (
    <div
      className="pointer-events-none fixed bottom-4 right-4 z-[100] flex items-center gap-2 rounded-lg border border-border bg-card/95 px-3 py-2 text-sm text-foreground shadow-lg backdrop-blur"
      role="status"
      aria-live="polite"
    >
      <Loader2 className="h-4 w-4 animate-spin text-indigo-600" />
      <span>{t("loading")}</span>
    </div>
  );
}
