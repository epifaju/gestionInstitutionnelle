"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo } from "react";
import { useForm } from "react-hook-form";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { renouvelerCdd } from "@/services/contrat.service";
import type { ContratResponse } from "@/types/contrat.types";

function schema(t: ReturnType<typeof useTranslations>, oldFin: string | null) {
  return z
    .object({
      nouvelleDateFin: z.string().min(1, t("zodDate")),
      motif: z.string().optional(),
      commentaire: z.string().optional(),
    })
    .superRefine((val, ctx) => {
      const nd = Date.parse(`${val.nouvelleDateFin}T00:00:00`);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (!Number.isFinite(nd)) {
        ctx.addIssue({ code: "custom", path: ["nouvelleDateFin"], message: t("zodDate") });
        return;
      }
      if (nd <= today.getTime()) {
        ctx.addIssue({ code: "custom", path: ["nouvelleDateFin"], message: t("zodAfterToday") });
      }
      if (oldFin) {
        const od = Date.parse(`${oldFin}T00:00:00`);
        if (Number.isFinite(od) && nd <= od) {
          ctx.addIssue({ code: "custom", path: ["nouvelleDateFin"], message: t("zodAfterOld") });
        }
      }
    });
}

type FormValues = z.infer<ReturnType<typeof schema>>;

export function RenouvellementModal({
  open,
  onOpenChange,
  contrat,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  contrat: ContratResponse | null;
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.renouvellement");

  const oldFin = contrat?.dateFinContrat ?? null;
  const resolver = useMemo(() => zodResolver(schema(t, oldFin)), [t, oldFin]);

  const form = useForm<FormValues>({
    resolver,
    defaultValues: { nouvelleDateFin: "", motif: "", commentaire: "" },
  });

  const nextNum = (contrat?.renouvellementNumero ?? 0) + 1;

  if (!contrat) return null;

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) form.reset({ nouvelleDateFin: "", motif: "", commentaire: "" });
      }}
    >
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>
            {t("subtitle", { nom: contrat.salarieNomComplet, n: nextNum })}
          </DialogDescription>
        </DialogHeader>

        <form
          className="space-y-3"
          onSubmit={form.handleSubmit(async (vals) => {
            await renouvelerCdd(contrat.id, {
              nouvelleDateFin: vals.nouvelleDateFin,
              motif: vals.motif || null,
              commentaire: vals.commentaire || null,
            });
            toast.success(t("toastOk"));
            onOpenChange(false);
            form.reset();
            onDone?.();
          })}
        >
          <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950 dark:border-amber-900/40 dark:bg-amber-950/30 dark:text-amber-100">
            {t("hintMax")}
          </div>

          <div className="space-y-1">
            <Label htmlFor="nouvelleDateFin">{t("dateFin")}</Label>
            <Input id="nouvelleDateFin" type="date" {...form.register("nouvelleDateFin")} />
            {form.formState.errors.nouvelleDateFin?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.nouvelleDateFin.message)}</p>
            ) : null}
          </div>

          <div className="space-y-1">
            <Label htmlFor="motif">{t("motif")}</Label>
            <Input id="motif" {...form.register("motif")} />
          </div>

          <div className="space-y-1">
            <Label htmlFor="commentaire">{t("commentaire")}</Label>
            <Textarea id="commentaire" rows={3} {...form.register("commentaire")} />
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
