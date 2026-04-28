"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo, useState } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { SalarieResponse } from "@/lib/types/rh";
import { enregistrerFormation } from "@/services/contrat.service";
import { listSalaries } from "@/services/salarie.service";

function schema(t: ReturnType<typeof useTranslations>) {
  const numOpt = z.preprocess(
    (v) => (v === "" || v == null ? undefined : Number(v)),
    z.number().finite().optional()
  );
  return z.object({
    salarieId: z.string().uuid(t("zodSalarie")),
    intitule: z.string().min(2, t("zodIntitule")),
    typeFormation: z.string().min(2, t("zodType")),
    organisme: z.string().optional(),
    dateRealisation: z.string().optional(),
    dateExpiration: z.string().min(1, t("zodDateExp")),
    periodiciteMois: z.preprocess(
      (v) => (v === "" || v == null ? undefined : Number(v)),
      z.number().int().min(1).max(120).optional()
    ),
    numeroCertificat: z.string().optional(),
    cout: numOpt,
  });
}

type FormValues = z.infer<ReturnType<typeof schema>>;

export function FormationModal({
  open,
  onOpenChange,
  defaultSalarieId,
  salaries,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  defaultSalarieId?: string | null;
  salaries?: SalarieResponse[];
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.formationModal");
  const resolver = useMemo(() => zodResolver(schema(t)), [t]);
  const [cert, setCert] = useState<File | null>(null);

  const form = useForm<FormValues>({
    resolver: resolver as Resolver<FormValues>,
    defaultValues: {
      salarieId: defaultSalarieId ?? "",
      intitule: "",
      typeFormation: "SST",
      organisme: "",
      dateRealisation: "",
      dateExpiration: "",
      periodiciteMois: 24,
      numeroCertificat: "",
      cout: undefined,
    },
  });

  useEffect(() => {
    if (defaultSalarieId) form.setValue("salarieId", defaultSalarieId);
  }, [defaultSalarieId, form]);

  const fetchSalaries = salaries === undefined;
  const { data: salariesPage } = useQuery({
    queryKey: ["rh", "salaries", "pick-formations", "ACTIF"],
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
            intitule: "",
            typeFormation: "SST",
            organisme: "",
            dateRealisation: "",
            dateExpiration: "",
            periodiciteMois: 24,
            numeroCertificat: "",
            cout: undefined,
          });
          setCert(null);
        }
      }}
    >
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("subtitle")}</DialogDescription>
        </DialogHeader>

        <form
          className="grid gap-3 md:grid-cols-2"
          onSubmit={form.handleSubmit(async (vals) => {
            await enregistrerFormation(
              vals.salarieId,
              {
                intitule: vals.intitule,
                typeFormation: vals.typeFormation,
                organisme: vals.organisme || null,
                dateRealisation: vals.dateRealisation || null,
                dateExpiration: vals.dateExpiration,
                periodiciteMois: vals.periodiciteMois ?? null,
                numeroCertificat: vals.numeroCertificat || null,
                cout: vals.cout === undefined || Number.isNaN(vals.cout) ? null : vals.cout,
              },
              cert
            );
            toast.success(t("toastOk"));
            onOpenChange(false);
            form.reset();
            setCert(null);
            onDone?.();
          })}
        >
          <div className="md:col-span-2 space-y-1">
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

          <div className="space-y-1 md:col-span-2">
            <Label htmlFor="int">{t("intitule")}</Label>
            <Input id="int" {...form.register("intitule")} />
            {form.formState.errors.intitule?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.intitule.message)}</p>
            ) : null}
          </div>

          <div className="space-y-1">
            <Label htmlFor="tf">{t("typeFormation")}</Label>
            <Input id="tf" {...form.register("typeFormation")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="org">{t("organisme")}</Label>
            <Input id="org" {...form.register("organisme")} />
          </div>

          <div className="space-y-1">
            <Label htmlFor="dr">{t("dateRealisation")}</Label>
            <Input id="dr" type="date" {...form.register("dateRealisation")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="de">{t("dateExpiration")}</Label>
            <Input id="de" type="date" {...form.register("dateExpiration")} />
            {form.formState.errors.dateExpiration?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.dateExpiration.message)}</p>
            ) : null}
          </div>

          <div className="space-y-1">
            <Label htmlFor="pm">{t("periodicite")}</Label>
            <Input id="pm" type="number" min={1} max={120} {...form.register("periodiciteMois", { valueAsNumber: true })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="nc">{t("numeroCertificat")}</Label>
            <Input id="nc" {...form.register("numeroCertificat")} />
          </div>

          <div className="space-y-1 md:col-span-2">
            <Label htmlFor="cout">{t("cout")}</Label>
            <Input id="cout" type="number" step="0.01" {...form.register("cout", { valueAsNumber: true })} />
          </div>

          <div className="md:col-span-2 space-y-1">
            <Label htmlFor="cert">{t("certificat")}</Label>
            <input id="cert" type="file" accept="application/pdf,image/*" onChange={(e) => setCert(e.target.files?.[0] ?? null)} />
          </div>

          <div className="md:col-span-2 flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={form.formState.isSubmitting}>
              {tc("close")}
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? tc("loading") : tc("save")}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
