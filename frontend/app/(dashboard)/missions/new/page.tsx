"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { createMission, createMissionForSalarie } from "@/services/missions.service";
import type { MissionRequest } from "@/lib/types/missions";
import { useAuthStore } from "@/lib/store";
import { listSalaries } from "@/services/salarie.service";
import { cn } from "@/lib/utils";

export default function NewMissionPage() {
  const t = useTranslations("Missions");
  const tc = useTranslations("Common");
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const role = user?.role ?? "";
  const canCreateForOthers = role === "ADMIN" || role === "RH";
  const [salarieId, setSalarieId] = useState<string>("");

  const [form, setForm] = useState<MissionRequest>({
    titre: "",
    destination: "",
    paysDestination: null,
    objectif: null,
    dateDepart: "",
    dateRetour: "",
    avanceDemandee: null,
    avanceDevise: "EUR",
  });

  const salariesQuery = useQuery({
    queryKey: ["rh", "salaries", "picklist"],
    queryFn: () => listSalaries({ page: 0, size: 200 }),
    enabled: canCreateForOthers,
  });

  const mut = useMutation({
    mutationFn: () => {
      if (canCreateForOthers) {
        if (!salarieId) {
          toast.error("Veuillez sélectionner un salarié.");
          return Promise.reject(new Error("Missing salarieId"));
        }
        return createMissionForSalarie(salarieId, form);
      }
      return createMission(form);
    },
    onSuccess: (m) => {
      toast.success(tc("successCreated"));
      router.push(`/missions/${m.id}`);
    },
  });

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t("createTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("createSubtitle")}</p>
      </div>

      <div className="space-y-3 rounded-lg border border-border bg-card p-4 text-card-foreground">
        {canCreateForOthers ? (
          <div className="space-y-1">
            <Label>Salarié</Label>
            <select
              value={salarieId}
              onChange={(e) => setSalarieId(e.target.value)}
              disabled={salariesQuery.isLoading || !salariesQuery.data}
              className={cn(
                "h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 md:text-sm dark:bg-input/30 dark:disabled:bg-input/80"
              )}
            >
              <option value="">{salariesQuery.isLoading ? "Chargement…" : "— Sélectionner —"}</option>
              {(salariesQuery.data?.content ?? []).map((s) => (
                <option key={s.id} value={s.id}>
                  {s.matricule} — {s.nom} {s.prenom}
                </option>
              ))}
            </select>
          </div>
        ) : null}

        <div className="space-y-1">
          <Label>{t("fTitre")}</Label>
          <Input value={form.titre} onChange={(e) => setForm((f) => ({ ...f, titre: e.target.value }))} />
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <div className="space-y-1">
            <Label>{t("fDestination")}</Label>
            <Input value={form.destination} onChange={(e) => setForm((f) => ({ ...f, destination: e.target.value }))} />
          </div>
          <div className="space-y-1">
            <Label>{t("fPays")}</Label>
            <Input
              value={form.paysDestination ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, paysDestination: e.target.value || null }))}
            />
          </div>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <div className="space-y-1">
            <Label>{t("fDepart")}</Label>
            <Input type="date" value={form.dateDepart} onChange={(e) => setForm((f) => ({ ...f, dateDepart: e.target.value }))} />
          </div>
          <div className="space-y-1">
            <Label>{t("fRetour")}</Label>
            <Input type="date" value={form.dateRetour} onChange={(e) => setForm((f) => ({ ...f, dateRetour: e.target.value }))} />
          </div>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <div className="space-y-1">
            <Label>{t("fAvanceDemandee")}</Label>
            <Input
              type="number"
              inputMode="decimal"
              value={form.avanceDemandee ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, avanceDemandee: e.target.value ? Number(e.target.value) : null }))}
            />
          </div>
          <div className="space-y-1">
            <Label>{t("fAvanceDevise")}</Label>
            <Input value={form.avanceDevise ?? "EUR"} onChange={(e) => setForm((f) => ({ ...f, avanceDevise: e.target.value }))} />
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={() => router.back()}>
            {tc("cancel")}
          </Button>
          <Button type="button" disabled={mut.isPending} onClick={() => mut.mutate()}>
            {mut.isPending ? tc("loading") : tc("create")}
          </Button>
        </div>
      </div>
    </div>
  );
}

