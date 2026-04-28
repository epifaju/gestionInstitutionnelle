"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo, useState } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { renouvelerFormation } from "@/services/contrat.service";
import type { FormationObligatoireResponse } from "@/types/contrat.types";

function schema(t: ReturnType<typeof useTranslations>) {
  const numOpt = z.preprocess(
    (v) => (v === "" || v == null ? undefined : Number(v)),
    z.number().finite().optional()
  );
  return z.object({
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

export function RenouvelerFormationModal({
  open,
  onOpenChange,
  formation,
  mode,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  formation: FormationObligatoireResponse | null;
  mode: "renew" | "cert";
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.renouvelerFormation");
  const resolver = useMemo(() => zodResolver(schema(t)), [t]);
  const [cert, setCert] = useState<File | null>(null);

  const form = useForm<FormValues>({
    resolver: resolver as Resolver<FormValues>,
    defaultValues: {
      intitule: "",
      typeFormation: "",
      organisme: "",
      dateRealisation: "",
      dateExpiration: "",
      periodiciteMois: 24,
      numeroCertificat: "",
      cout: undefined,
    },
  });

  useEffect(() => {
    if (!open || !formation) return;
    form.reset({
      intitule: formation.intitule,
      typeFormation: formation.typeFormation,
      organisme: formation.organisme ?? "",
      dateRealisation: formation.dateRealisation ?? "",
      dateExpiration: formation.dateExpiration,
      periodiciteMois: formation.periodiciteMois ?? 24,
      numeroCertificat: formation.numeroCertificat ?? "",
      cout: formation.cout === null || formation.cout === undefined ? undefined : Number(formation.cout),
    });
  }, [open, formation, form]);

  if (!formation) return null;

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) setCert(null);
      }}
    >
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{mode === "cert" ? t("titleCert") : t("title")}</DialogTitle>
          <DialogDescription>{t("subtitle", { nom: formation.salarieNomComplet })}</DialogDescription>
        </DialogHeader>

        <form
          className="grid gap-3 md:grid-cols-2"
          onSubmit={form.handleSubmit(async (vals) => {
            if (mode === "cert" && !cert) {
              toast.error(t("zodCert"));
              return;
            }
            await renouvelerFormation(
              formation.id,
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
            toast.success(mode === "cert" ? t("toastOkCert") : t("toastOk"));
            onOpenChange(false);
            setCert(null);
            onDone?.();
          })}
        >
          <div className="space-y-1 md:col-span-2">
            <Label htmlFor="int">{t("intitule")}</Label>
            <Input id="int" {...form.register("intitule")} />
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

          <DialogFooter className="md:col-span-2">
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
