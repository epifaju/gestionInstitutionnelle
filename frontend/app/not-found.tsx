"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  const t = useTranslations("Errors");
  const tc = useTranslations("Common");

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 p-6 text-center">
      <h1 className="text-2xl font-semibold text-slate-900">{t("pageNotFound")}</h1>
      <p className="max-w-md text-sm text-slate-600">{t("pageNotFoundDescription")}</p>
      <Link href="/dashboard">
        <Button type="button">{tc("backHome")}</Button>
      </Link>
    </div>
  );
}
