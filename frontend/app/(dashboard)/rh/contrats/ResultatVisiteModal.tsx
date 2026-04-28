"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { enregistrerResultatVisite } from "@/services/contrat.service";

const RESULTATS = ["APTE", "APTE_AMENAGEMENT", "INAPTE", "EN_ATTENTE"] as const;
const ResultatEnum = z.enum(RESULTATS);

function schema(t: ReturnType<typeof useTranslations>) {
  return z
    .object({
      resultat: ResultatEnum,
      restrictions: z.string().optional(),
    })
    .superRefine((val, ctx) => {
      if (val.resultat === "APTE_AMENAGEMENT") {
        const r = (val.restrictions ?? "").trim();
        if (!r) ctx.addIssue({ code: "custom", path: ["restrictions"], message: t("zodRestrictions") });
      }
    });
}

type FormValues = z.infer<ReturnType<typeof schema>>;

export function ResultatVisiteModal({
  open,
  onOpenChange,
  visiteId,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  visiteId: string | null;
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.resultatVisite");
  const resolver = useMemo(() => zodResolver(schema(t)), [t]);
  const [cr, setCr] = useState<File | null>(null);

  const form = useForm<FormValues>({
    resolver,
    defaultValues: { resultat: "APTE", restrictions: "" },
  });

  const res = form.watch("resultat");

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) {
          form.reset({ resultat: "APTE", restrictions: "" });
          setCr(null);
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
            if (!visiteId) return;
            await enregistrerResultatVisite(
              visiteId,
              vals.resultat,
              vals.resultat === "APTE_AMENAGEMENT" ? vals.restrictions ?? null : null,
              cr
            );
            toast.success(t("toastOk"));
            onOpenChange(false);
            form.reset();
            setCr(null);
            onDone?.();
          })}
        >
          <div className="space-y-2">
            <Label htmlFor="res">{t("resultat")}</Label>
            <select id="res" className="h-9 w-full rounded-lg border border-input bg-transparent px-2 text-sm" {...form.register("resultat")}>
              {RESULTATS.map((r) => (
                <option key={r} value={r}>
                  {t(`resultats.${r}` as const)}
                </option>
              ))}
            </select>
          </div>

          {res === "APTE_AMENAGEMENT" ? (
            <div className="space-y-1">
              <Label htmlFor="rest">{t("restrictions")}</Label>
              <Textarea id="rest" rows={3} {...form.register("restrictions")} />
              {form.formState.errors.restrictions?.message ? (
                <p className="text-sm text-red-600">{String(form.formState.errors.restrictions.message)}</p>
              ) : null}
            </div>
          ) : null}

          <div className="space-y-1">
            <Label htmlFor="cr">{t("cr")}</Label>
            <input id="cr" type="file" accept="application/pdf" onChange={(e) => setCr(e.target.files?.[0] ?? null)} />
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={form.formState.isSubmitting}>
              {tc("close")}
            </Button>
            <Button type="submit" disabled={!visiteId || form.formState.isSubmitting}>
              {form.formState.isSubmitting ? tc("loading") : tc("save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
