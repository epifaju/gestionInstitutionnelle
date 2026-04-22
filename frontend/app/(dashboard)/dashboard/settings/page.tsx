"use client";

import { useTranslations } from "next-intl";
import { useAuthStore } from "@/lib/store";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { put } from "@/lib/api";
import { useState } from "react";
import { setLocaleCookie } from "@/lib/locale-cookie";
import { useAppLocale } from "@/lib/locale-context";

export default function SettingsPage() {
  const t = useTranslations("Settings");
  const tc = useTranslations("Common");
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setAuth);
  const token = useAuthStore((s) => s.accessToken);
  const [saving, setSaving] = useState<"fr" | "en" | "pt-PT" | null>(null);
  const { setLocale } = useAppLocale();

  async function changeLangue(langue: "fr" | "en" | "pt-PT") {
    if (!user || !token) return;
    setSaving(langue);
    try {
      const updated = await put<{
        id: string;
        email: string;
        nom: string | null;
        prenom: string | null;
        role: string;
        organisationId: string;
        organisationNom: string | null;
        langue?: string | null;
      }>("auth/me/langue", { langue });

      setLocaleCookie(langue);
      setLocale(langue);
      // met à jour l'utilisateur en store sans toucher au token
      setUser({ ...updated }, token);
    } finally {
      setSaving(null);
    }
  }

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
        <CardContent className="space-y-4 text-sm">
          <div>
            <p className="text-slate-500">{t("email")}</p>
            <p className="font-medium text-slate-900">{user?.email ?? "—"}</p>
          </div>
          <div>
            <p className="text-slate-500">{t("organisation")}</p>
            <p className="font-medium text-slate-900">{user?.organisationNom ?? "—"}</p>
          </div>
          <div>
            <p className="text-slate-500">{t("languageLabel")}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button
                type="button"
                variant={user?.langue === "fr" || !user?.langue ? "default" : "secondary"}
                onClick={() => changeLangue("fr")}
                disabled={!!saving}
              >
                {tc("localeFr")}
              </Button>
              <Button
                type="button"
                variant={user?.langue === "en" ? "default" : "secondary"}
                onClick={() => changeLangue("en")}
                disabled={!!saving}
              >
                {tc("localeEn")}
              </Button>
              <Button
                type="button"
                variant={user?.langue === "pt-PT" ? "default" : "secondary"}
                onClick={() => changeLangue("pt-PT")}
                disabled={!!saving}
              >
                {tc("localePt")}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
