"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { SalarieResponse } from "@/lib/types/rh";
import { enregistrerTitreSejour } from "@/services/contrat.service";
import { listSalaries } from "@/services/salarie.service";

function schema(t: ReturnType<typeof useTranslations>) {
  return z.object({
    salarieId: z.string().uuid(t("zodSalarie")),
    typeDocument: z.string().min(2, t("zodType")),
    numeroDocument: z.string().optional(),
    paysEmetteur: z.string().optional(),
    dateEmission: z.string().optional(),
    dateExpiration: z.string().min(1, t("zodDateExp")),
    autoriteEmettrice: z.string().optional(),
  });
}

type FormValues = z.infer<ReturnType<typeof schema>>;

export function TitreSejourModal({
  open,
  onOpenChange,
  defaultSalarieId,
  preset,
  salaries,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  defaultSalarieId?: string | null;
  salaries?: SalarieResponse[];
  preset?: {
    typeDocument?: string;
    numeroDocument?: string | null;
    paysEmetteur?: string | null;
    dateEmission?: string | null;
    dateExpiration?: string;
    autoriteEmettrice?: string | null;
  } | null;
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.titreModal");
  const resolver = useMemo(() => zodResolver(schema(t)), [t]);
  const [doc, setDoc] = useState<File | null>(null);

  const form = useForm<FormValues>({
    resolver,
    defaultValues: {
      salarieId: defaultSalarieId ?? "",
      typeDocument: "Titre de séjour",
      numeroDocument: "",
      paysEmetteur: "",
      dateEmission: "",
      dateExpiration: "",
      autoriteEmettrice: "",
    },
  });

  useEffect(() => {
    if (defaultSalarieId) form.setValue("salarieId", defaultSalarieId);
  }, [defaultSalarieId, form]);

  useEffect(() => {
    if (!open || !preset) return;
    form.reset({
      salarieId: defaultSalarieId ?? "",
      typeDocument: preset.typeDocument ?? "",
      numeroDocument: preset.numeroDocument ?? "",
      paysEmetteur: preset.paysEmetteur ?? "",
      dateEmission: preset.dateEmission ?? "",
      dateExpiration: preset.dateExpiration ?? "",
      autoriteEmettrice: preset.autoriteEmettrice ?? "",
    });
  }, [open, preset, defaultSalarieId, form]);

  const fetchSalaries = salaries === undefined;
  const { data: salariesPage } = useQuery({
    queryKey: ["rh", "salaries", "pick-titres", "ACTIF"],
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
            typeDocument: "Titre de séjour",
            numeroDocument: "",
            paysEmetteur: "",
            dateEmission: "",
            dateExpiration: "",
            autoriteEmettrice: "",
          });
          setDoc(null);
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
            await enregistrerTitreSejour(
              vals.salarieId,
              {
                typeDocument: vals.typeDocument,
                numeroDocument: vals.numeroDocument || null,
                paysEmetteur: vals.paysEmetteur || null,
                dateEmission: vals.dateEmission || null,
                dateExpiration: vals.dateExpiration,
                autoriteEmettrice: vals.autoriteEmettrice || null,
              },
              doc
            );
            toast.success(t("toastOk"));
            onOpenChange(false);
            form.reset();
            setDoc(null);
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

          <div className="space-y-1">
            <Label htmlFor="td">{t("typeDocument")}</Label>
            <Input id="td" {...form.register("typeDocument")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="nd">{t("numero")}</Label>
            <Input id="nd" {...form.register("numeroDocument")} />
          </div>

          <div className="space-y-1">
            <Label htmlFor="pays">{t("pays")}</Label>
            <Input id="pays" {...form.register("paysEmetteur")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="ae">{t("autorite")}</Label>
            <Input id="ae" {...form.register("autoriteEmettrice")} />
          </div>

          <div className="space-y-1">
            <Label htmlFor="de">{t("dateEmission")}</Label>
            <Input id="de" type="date" {...form.register("dateEmission")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="dx">{t("dateExpiration")}</Label>
            <Input id="dx" type="date" {...form.register("dateExpiration")} />
            {form.formState.errors.dateExpiration?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.dateExpiration.message)}</p>
            ) : null}
          </div>

          <div className="md:col-span-2 space-y-1">
            <Label htmlFor="doc">{t("scan")}</Label>
            <input id="doc" type="file" accept="application/pdf,image/*" onChange={(e) => setDoc(e.target.files?.[0] ?? null)} />
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
