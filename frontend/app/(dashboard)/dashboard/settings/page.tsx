"use client";

import { useTranslations } from "next-intl";
import { useAuthStore } from "@/lib/store";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { get, put } from "@/lib/api";
import { useMemo, useState } from "react";
import { setLocaleCookie } from "@/lib/locale-cookie";
import { useAppLocale } from "@/lib/locale-context";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useAppTheme } from "@/lib/theme-context";

export default function SettingsPage() {
  const t = useTranslations("Settings");
  const tc = useTranslations("Common");
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setAuth);
  const token = useAuthStore((s) => s.accessToken);
  const [saving, setSaving] = useState<"fr" | "en" | "pt-PT" | null>(null);
  const { setLocale } = useAppLocale();
  const { theme, setTheme } = useAppTheme();

  const [prenom, setPrenom] = useState(user?.prenom ?? "");
  const [nom, setNom] = useState(user?.nom ?? "");
  const [email, setEmail] = useState(user?.email ?? "");

  const NOTIF_TYPES = useMemo(
    () => [
      "CONGE_SOUMIS",
      "CONGE_VALIDE",
      "CONGE_REJETE",
      "FACTURE_EN_RETARD",
      "DOCUMENT_EXPIRE_BIENTOT",
      "BUDGET_ALERTE_80PCT",
      "CONTRAT_EXPIRE_BIENTOT",
      "BIEN_MAINTENANCE",
      "SALAIRE_DU",
    ],
    []
  );

  const prefsQuery = useQuery({
    queryKey: ["me", "preferences"],
    queryFn: () =>
      get<{
        theme: "system" | "light" | "dark";
        notificationsUiEnabled: string[];
        notificationsEmailEnabled: string[];
      }>("auth/me/preferences"),
    enabled: !!token,
  });

  const prefs = prefsQuery.data ?? { theme: "system" as const, notificationsUiEnabled: [], notificationsEmailEnabled: [] };

  const mutProfile = useMutation({
    mutationFn: () =>
      put<{
        user: {
          id: string;
          email: string;
          nom: string | null;
          prenom: string | null;
          role: string;
          organisationId: string;
          organisationNom: string | null;
          langue?: string | null;
        };
        accessToken: string;
        expiresInSeconds: number;
      }>("auth/me/profile", { prenom, nom, email }),
    onSuccess: (res) => {
      setUser({ ...res.user }, res.accessToken, res.expiresInSeconds);
      toast.success(tc("successSaved"));
    },
  });

  const mutPassword = useMutation({
    mutationFn: (payload: { currentPassword: string; newPassword: string }) => put<{ message: string }>("auth/me/password", payload),
    onSuccess: () => toast.success(tc("successSaved")),
  });

  const mutPrefs = useMutation({
    mutationFn: (payload: {
      theme: "system" | "light" | "dark";
      notificationsUiEnabled: string[];
      notificationsEmailEnabled: string[];
    }) => put<typeof prefs>("auth/me/preferences", payload),
    onSuccess: (updated) => {
      // sync theme immediately
      setTheme(updated.theme);
    },
  });

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPassword2, setNewPassword2] = useState("");

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
      toast.success(t("languageSaved"));
    } finally {
      setSaving(null);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("accountTitle")}</CardTitle>
            <CardDescription>{t("accountSubtitle")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-2 gap-2">
              <div>
                <Label>{t("firstName")}</Label>
                <Input value={prenom} onChange={(e) => setPrenom(e.target.value)} />
              </div>
              <div>
                <Label>{t("lastName")}</Label>
                <Input value={nom} onChange={(e) => setNom(e.target.value)} />
              </div>
            </div>
            <div>
              <Label>{t("email")}</Label>
              <Input value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">{t("organisation")}</p>
              <p className="text-sm font-medium text-foreground">{user?.organisationNom ?? tc("emDash")}</p>
            </div>
            <Button type="button" onClick={() => mutProfile.mutate()} disabled={mutProfile.isPending || !token}>
              {tc("save")}
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("securityTitle")}</CardTitle>
            <CardDescription>{t("securitySubtitle")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div>
              <Label>{t("currentPassword")}</Label>
              <Input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
            </div>
            <div>
              <Label>{t("newPassword")}</Label>
              <Input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
            </div>
            <div>
              <Label>{t("confirmPassword")}</Label>
              <Input type="password" value={newPassword2} onChange={(e) => setNewPassword2(e.target.value)} />
            </div>
            <Button
              type="button"
              onClick={() => {
                if (newPassword !== newPassword2) {
                  toast.error(t("passwordMismatch"));
                  return;
                }
                mutPassword.mutate({ currentPassword, newPassword });
                setCurrentPassword("");
                setNewPassword("");
                setNewPassword2("");
              }}
              disabled={mutPassword.isPending || !token || !currentPassword || !newPassword}
            >
              {t("updatePassword")}
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("preferencesTitle")}</CardTitle>
          <CardDescription>{t("preferencesSubtitle")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="space-y-2">
            <p className="text-sm font-medium text-foreground">{t("languageLabel")}</p>
            <p className="text-xs text-muted-foreground">{t("localeHint")}</p>
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

          <div className="space-y-2">
            <p className="text-sm font-medium text-foreground">{t("themeLabel")}</p>
            <div className="flex flex-wrap gap-2">
              {(["system", "light", "dark"] as const).map((v) => (
                <Button
                  key={v}
                  type="button"
                  variant={theme === v ? "default" : "secondary"}
                  onClick={() => {
                    setTheme(v);
                    mutPrefs.mutate({ ...prefs, theme: v });
                  }}
                  disabled={!token}
                >
                  {t(`theme_${v}`)}
                </Button>
              ))}
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <p className="text-sm font-medium text-foreground">{t("notificationsUiTitle")}</p>
              <p className="text-xs text-muted-foreground">{t("notificationsUiHint")}</p>
              <div className="space-y-2">
                {NOTIF_TYPES.map((nt) => {
                  const checked = prefs.notificationsUiEnabled.length === 0 ? true : prefs.notificationsUiEnabled.includes(nt);
                  return (
                    <label
                      key={nt}
                      className="flex items-center justify-between gap-3 rounded-md border border-border bg-card px-3 py-2 text-sm text-card-foreground"
                    >
                      <span>{t(`notif_${nt}`)}</span>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={(e) => {
                          const current = prefs.notificationsUiEnabled.length === 0 ? [...NOTIF_TYPES] : prefs.notificationsUiEnabled;
                          const next = e.target.checked ? Array.from(new Set([...current, nt])) : current.filter((x) => x !== nt);
                          mutPrefs.mutate({ ...prefs, notificationsUiEnabled: next });
                        }}
                        disabled={!token}
                      />
                    </label>
                  );
                })}
              </div>
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium text-foreground">{t("notificationsEmailTitle")}</p>
              <p className="text-xs text-muted-foreground">{t("notificationsEmailHint")}</p>
              <div className="space-y-2">
                {NOTIF_TYPES.map((nt) => {
                  const checked = prefs.notificationsEmailEnabled.includes(nt);
                  return (
                    <label
                      key={nt}
                      className="flex items-center justify-between gap-3 rounded-md border border-border bg-card px-3 py-2 text-sm text-card-foreground"
                    >
                      <span>{t(`notif_${nt}`)}</span>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={(e) => {
                          const next = e.target.checked
                            ? Array.from(new Set([...prefs.notificationsEmailEnabled, nt]))
                            : prefs.notificationsEmailEnabled.filter((x) => x !== nt);
                          mutPrefs.mutate({ ...prefs, notificationsEmailEnabled: next });
                        }}
                        disabled={!token}
                      />
                    </label>
                  );
                })}
              </div>
            </div>
          </div>

          {prefsQuery.isLoading ? <p className="text-xs text-muted-foreground">{tc("loading")}</p> : null}
        </CardContent>
      </Card>
    </div>
  );
}
