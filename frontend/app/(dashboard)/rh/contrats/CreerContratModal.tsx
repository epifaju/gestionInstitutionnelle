"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { creerContrat } from "@/services/contrat.service";
import type { ContratRequest } from "@/types/contrat.types";

function buildSchema(t: (key: string) => string) {
  return z
    .object({
      typeContrat: z.enum(["CDI", "CDD"]),
      dateDebutContrat: z.string().min(1, t("zodDateDebut")),
      dateFinContrat: z.string().optional(),
      motifCdd: z.string().optional(),
      intitulePoste: z.string().optional(),
    })
    .superRefine((data, ctx) => {
      if (data.typeContrat === "CDD") {
        if (!data.dateFinContrat?.trim()) {
          ctx.addIssue({ code: "custom", path: ["dateFinContrat"], message: t("zodDateFinCdd") });
        } else if (
          data.dateDebutContrat &&
          data.dateFinContrat.trim() <= data.dateDebutContrat.trim()
        ) {
          ctx.addIssue({ code: "custom", path: ["dateFinContrat"], message: t("zodDateFinAfterDebut") });
        }
        if (!data.motifCdd?.trim()) {
          ctx.addIssue({ code: "custom", path: ["motifCdd"], message: t("zodMotifCdd") });
        }
      }
    });
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>;

export function CreerContratModal({
  open,
  onOpenChange,
  salarieId,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  salarieId: string;
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.creerContrat");
  const resolver = useMemo(() => zodResolver(buildSchema(t)), [t]);

  const form = useForm<FormValues>({
    resolver: resolver as Resolver<FormValues>,
    defaultValues: {
      typeContrat: "CDI",
      dateDebutContrat: "",
      dateFinContrat: "",
      motifCdd: "",
      intitulePoste: "",
    },
  });

  const type = form.watch("typeContrat");

  useEffect(() => {
    if (open) {
      form.reset({
        typeContrat: "CDI",
        dateDebutContrat: "",
        dateFinContrat: "",
        motifCdd: "",
        intitulePoste: "",
      });
    }
  }, [open, form]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("subtitle")}</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-3"
          onSubmit={form.handleSubmit(async (v) => {
            const body: ContratRequest = {
              typeContrat: v.typeContrat,
              dateDebutContrat: v.dateDebutContrat,
              dateFinContrat: v.typeContrat === "CDD" ? (v.dateFinContrat?.trim() || null) : null,
              dateFinPeriodeEssai: null,
              dureeEssaiMois: null,
              numeroContrat: null,
              intitulePoste: v.intitulePoste?.trim() || null,
              motifCdd: v.typeContrat === "CDD" ? (v.motifCdd?.trim() ?? null) : null,
              conventionCollective: null,
            };
            try {
              await creerContrat(salarieId, body);
              toast.success(t("toastOk"));
              onOpenChange(false);
              await onDone?.();
            } catch {
              toast.error(tc("errorGeneric"));
            }
          })}
        >
          <div>
            <Label htmlFor="cc-type">{t("typeLabel")}</Label>
            <select
              id="cc-type"
              className="mt-1 flex h-9 w-full rounded-md border border-input bg-background px-2 text-sm"
              {...form.register("typeContrat")}
            >
              <option value="CDI">{t("typeCdi")}</option>
              <option value="CDD">{t("typeCdd")}</option>
            </select>
          </div>
          <div>
            <Label htmlFor="cc-debut">{t("dateDebut")}</Label>
            <Input id="cc-debut" type="date" {...form.register("dateDebutContrat")} />
            {form.formState.errors.dateDebutContrat ? (
              <p className="mt-1 text-xs text-red-600">{form.formState.errors.dateDebutContrat.message}</p>
            ) : null}
          </div>
          {type === "CDD" ? (
            <>
              <div>
                <Label htmlFor="cc-fin">{t("dateFin")}</Label>
                <Input id="cc-fin" type="date" {...form.register("dateFinContrat")} />
                {form.formState.errors.dateFinContrat ? (
                  <p className="mt-1 text-xs text-red-600">{form.formState.errors.dateFinContrat.message}</p>
                ) : null}
              </div>
              <div>
                <Label htmlFor="cc-motif">{t("motifCdd")}</Label>
                <Input id="cc-motif" {...form.register("motifCdd")} placeholder={t("motifPlaceholder")} />
                {form.formState.errors.motifCdd ? (
                  <p className="mt-1 text-xs text-red-600">{form.formState.errors.motifCdd.message}</p>
                ) : null}
              </div>
            </>
          ) : null}
          <div>
            <Label htmlFor="cc-poste">{t("intitulePoste")}</Label>
            <Input id="cc-poste" {...form.register("intitulePoste")} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {tc("cancel")}
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? tc("loading") : tc("create")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
