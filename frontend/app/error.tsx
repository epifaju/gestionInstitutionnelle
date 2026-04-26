"use client";

import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations("Errors");
  const tc = useTranslations("Common");

  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-6 text-center">
      <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
      <p className="max-w-md text-sm text-muted-foreground">{t("globalDescription")}</p>
      <div className="flex gap-2">
        <Button type="button" variant="outline" onClick={() => reset()}>
          {tc("retry")}
        </Button>
        <Button type="button" onClick={() => (window.location.href = "/dashboard")}>
          {tc("backHome")}
        </Button>
      </div>
    </div>
  );
}
