"use client";

import { useTranslations } from "next-intl";
import { useAuthStore } from "@/lib/store";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function SettingsPage() {
  const t = useTranslations("Settings");
  const user = useAuthStore((s) => s.user);

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{t("title")}</h1>
        <p className="text-sm text-slate-600">{t("subtitle")}</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>{t("title")}</CardTitle>
          <CardDescription>{t("localeHint")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <div>
            <p className="text-slate-500">{t("email")}</p>
            <p className="font-medium text-slate-900">{user?.email ?? "—"}</p>
          </div>
          <div>
            <p className="text-slate-500">{t("organisation")}</p>
            <p className="font-medium text-slate-900">{user?.organisationNom ?? "—"}</p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
