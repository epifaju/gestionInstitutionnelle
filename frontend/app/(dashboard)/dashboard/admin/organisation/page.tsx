"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { get } from "@/lib/api";
import { useAuthStore, type UserInfo } from "@/lib/store";
import { getOrganisationSettings, updateOrganisationSettings } from "@/services/admin.service";

export default function OrganisationSettingsPage() {
  const t = useTranslations("Admin.organisation");
  const tc = useTranslations("Common");
  const user = useAuthStore((s) => s.user);
  const accessToken = useAuthStore((s) => s.accessToken);
  const setAuth = useAuthStore((s) => s.setAuth);
  const isAdmin = user?.role === "ADMIN";
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "organisation"],
    queryFn: getOrganisationSettings,
    enabled: isAdmin,
  });

  const [nom, setNom] = useState("");
  const [titreApp, setTitreApp] = useState("");
  const [slogan, setSlogan] = useState("");
  const [type, setType] = useState("");
  const [pays, setPays] = useState("");
  const [deviseDefaut, setDeviseDefaut] = useState("EUR");
  const [logoUrl, setLogoUrl] = useState("");
  const [seuilJustificatif, setSeuilJustificatif] = useState("500");
  const [alerteBudgetPct, setAlerteBudgetPct] = useState("80");

  useEffect(() => {
    if (!data) return;
    setNom(data.nom ?? "");
    setTitreApp(data.titreApp ?? "");
    setSlogan(data.slogan ?? "");
    setType(data.type ?? "");
    setPays(data.pays ?? "");
    setDeviseDefaut(data.deviseDefaut ?? "EUR");
    setLogoUrl(data.logoUrl ?? "");
    setSeuilJustificatif(String(data.seuilJustificatif ?? "500"));
    setAlerteBudgetPct(String(data.alerteBudgetPct ?? 80));
  }, [data]);

  const mut = useMutation({
    mutationFn: () =>
      updateOrganisationSettings({
        nom: nom.trim(),
        titreApp: titreApp.trim() || null,
        slogan: slogan.trim() || null,
        type: type.trim(),
        pays: pays.trim() || null,
        deviseDefaut: deviseDefaut.trim().toUpperCase(),
        logoUrl: logoUrl.trim() || null,
        seuilJustificatif,
        alerteBudgetPct: Number.parseInt(alerteBudgetPct, 10),
      }),
    onSuccess: async () => {
      qc.invalidateQueries({ queryKey: ["admin", "organisation"] });
      try {
        if (accessToken) {
          const me = await get<UserInfo>("auth/me");
          setAuth(me, accessToken);
        }
      } catch {
        /* ignore: sidebar se mettra à jour au prochain /me */
      }
      toast.success(t("toastSaved"));
    },
    onError: (e: unknown) => {
      const msg =
        e && typeof e === "object" && "message" in e
          ? String((e as { message: unknown }).message)
          : tc("errorGeneric");
      toast.error(msg);
    },
  });

  if (!isAdmin) {
    return <p className="text-sm text-muted-foreground">{t("restricted")}</p>;
  }

  if (isLoading || !data) {
    return <Skeleton className="h-96 w-full" />;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t("cardIdentity")}</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              mut.mutate();
            }}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5 sm:col-span-2">
                <Label htmlFor="org-nom">{t("labelNom")}</Label>
                <Input id="org-nom" value={nom} onChange={(e) => setNom(e.target.value)} required />
              </div>
              <div className="space-y-1.5 sm:col-span-2">
                <Label htmlFor="org-titre">{t("labelTitreApp")}</Label>
                <Input
                  id="org-titre"
                  value={titreApp}
                  onChange={(e) => setTitreApp(e.target.value)}
                  maxLength={120}
                />
                <p className="text-xs text-muted-foreground">{t("hintTitreApp")}</p>
              </div>
              <div className="space-y-1.5 sm:col-span-2">
                <Label htmlFor="org-slogan">{t("labelSlogan")}</Label>
                <Input
                  id="org-slogan"
                  value={slogan}
                  onChange={(e) => setSlogan(e.target.value)}
                  maxLength={200}
                />
                <p className="text-xs text-muted-foreground">{t("hintSlogan")}</p>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="org-type">{t("labelType")}</Label>
                <Input id="org-type" value={type} onChange={(e) => setType(e.target.value)} required />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="org-pays">{t("labelPays")}</Label>
                <Input id="org-pays" value={pays} onChange={(e) => setPays(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="org-devise">{t("labelDevise")}</Label>
                <Input
                  id="org-devise"
                  value={deviseDefaut}
                  maxLength={3}
                  onChange={(e) => setDeviseDefaut(e.target.value.toUpperCase())}
                  required
                />
              </div>
              <div className="space-y-1.5 sm:col-span-2">
                <Label htmlFor="org-logo">{t("labelLogo")}</Label>
                <Input
                  id="org-logo"
                  value={logoUrl}
                  onChange={(e) => setLogoUrl(e.target.value)}
                  placeholder="https://…"
                />
              </div>
            </div>

            <div className="border-t border-border pt-4">
              <p className="mb-3 text-sm font-medium text-foreground">{t("cardThresholds")}</p>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="org-seuil">{t("labelSeuilJustificatif")}</Label>
                  <Input
                    id="org-seuil"
                    type="number"
                    min={0}
                    step="0.01"
                    value={seuilJustificatif}
                    onChange={(e) => setSeuilJustificatif(e.target.value)}
                    required
                  />
                  <p className="text-xs text-muted-foreground">{t("hintSeuilJustificatif")}</p>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="org-alerte">{t("labelAlerteBudget")}</Label>
                  <Input
                    id="org-alerte"
                    type="number"
                    min={1}
                    max={100}
                    value={alerteBudgetPct}
                    onChange={(e) => setAlerteBudgetPct(e.target.value)}
                    required
                  />
                  <p className="text-xs text-muted-foreground">{t("hintAlerteBudget")}</p>
                </div>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={mut.isPending}>
                {mut.isPending ? tc("loading") : tc("save")}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
