"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { createMission } from "@/services/missions.service";
import type { MissionRequest } from "@/lib/types/missions";

export default function NewMissionPage() {
  const t = useTranslations("Missions");
  const tc = useTranslations("Common");
  const router = useRouter();

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

  const mut = useMutation({
    mutationFn: () => createMission(form),
    onSuccess: (m) => {
      toast.success(tc("successCreated"));
      router.push(`/missions/${m.id}`);
    },
  });

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{t("createTitle")}</h1>
        <p className="text-sm text-slate-600">{t("createSubtitle")}</p>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-4 space-y-3">
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

