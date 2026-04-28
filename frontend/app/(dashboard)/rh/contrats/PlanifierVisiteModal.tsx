"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { SalarieResponse } from "@/lib/types/rh";
import { creerVisite } from "@/services/contrat.service";
import { listSalaries } from "@/services/salarie.service";

function schema(t: ReturnType<typeof useTranslations>) {
  return z.object({
    salarieId: z.string().uuid(t("zodSalarie")),
    typeVisite: z.string().min(2, t("zodType")),
    datePlanifiee: z.string().min(1, t("zodDate")),
    medecin: z.string().optional(),
    centreMedical: z.string().optional(),
    periodiciteMois: z.preprocess(
      (v) => (v === "" || v == null ? undefined : Number(v)),
      z.number().int().min(1).max(120).optional()
    ),
  });
}

type FormValues = z.infer<ReturnType<typeof schema>>;

export function PlanifierVisiteModal({
  open,
  onOpenChange,
  defaultSalarieId,
  salaries,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  defaultSalarieId?: string | null;
  /** Liste déjà chargée par la page (onglet Visites) — évite une 2ᵉ requête et les listes vides si le cache n’est pas prêt. */
  salaries?: SalarieResponse[];
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.planifierVisite");
  const resolver = useMemo(() => zodResolver(schema(t)), [t]);

  const form = useForm<FormValues>({
    resolver: resolver as Resolver<FormValues>,
    defaultValues: {
      salarieId: defaultSalarieId ?? "",
      typeVisite: "VISITE_REPRISE",
      datePlanifiee: "",
      medecin: "",
      centreMedical: "",
      periodiciteMois: 24,
    },
  });

  useEffect(() => {
    if (defaultSalarieId) form.setValue("salarieId", defaultSalarieId);
  }, [defaultSalarieId, form]);

  const fetchSalaries = salaries === undefined;
  const { data: salariesPage } = useQuery({
    queryKey: ["rh", "salaries", "pick-visites", "ACTIF"],
    queryFn: () => listSalaries({ page: 0, size: 500, statut: "ACTIF" }),
    enabled: open && fetchSalaries,
  });
  const salarieOptions = salaries !== undefined ? salaries : (salariesPage?.content ?? []);

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) {
          form.reset({
            salarieId: defaultSalarieId ?? "",
            typeVisite: "VISITE_REPRISE",
            datePlanifiee: "",
            medecin: "",
            centreMedical: "",
            periodiciteMois: 24,
          });
        }
      }}
    >
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("subtitle")}</DialogDescription>
        </DialogHeader>

        <form
          className="space-y-3"
          onSubmit={form.handleSubmit(async (vals) => {
            await creerVisite(vals.salarieId, {
              typeVisite: vals.typeVisite,
              datePlanifiee: vals.datePlanifiee,
              dateRealisee: null,
              medecin: vals.medecin || null,
              centreMedical: vals.centreMedical || null,
              resultat: "EN_ATTENTE",
              restrictions: null,
              periodiciteMois: vals.periodiciteMois ?? 24,
            });
            toast.success(t("toastOk"));
            onOpenChange(false);
            form.reset();
            onDone?.();
          })}
        >
          <div className="space-y-1">
            <Label htmlFor="sid">{t("salarie")}</Label>
            <select id="sid" className="h-9 w-full rounded-lg border border-input bg-transparent px-2 text-sm" {...form.register("salarieId")}>
              <option value="">{t("salariePlaceholder")}</option>
              {salarieOptions.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.prenom} {s.nom} · {s.matricule}
                </option>
              ))}
            </select>
            {form.formState.errors.salarieId?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.salarieId.message)}</p>
            ) : null}
          </div>

          <div className="space-y-1">
            <Label htmlFor="tv">{t("typeVisite")}</Label>
            <Input id="tv" {...form.register("typeVisite")} />
            {form.formState.errors.typeVisite?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.typeVisite.message)}</p>
            ) : null}
          </div>

          <div className="space-y-1">
            <Label htmlFor="dp">{t("datePlanifiee")}</Label>
            <Input id="dp" type="date" {...form.register("datePlanifiee")} />
            {form.formState.errors.datePlanifiee?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.datePlanifiee.message)}</p>
            ) : null}
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="m">{t("medecin")}</Label>
              <Input id="m" {...form.register("medecin")} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="c">{t("centre")}</Label>
              <Input id="c" {...form.register("centreMedical")} />
            </div>
          </div>

          <div className="space-y-1">
            <Label htmlFor="p">{t("periodicite")}</Label>
            <Input id="p" type="number" min={1} max={120} {...form.register("periodiciteMois", { valueAsNumber: true })} />
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={form.formState.isSubmitting}>
              {tc("close")}
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? tc("loading") : tc("save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
