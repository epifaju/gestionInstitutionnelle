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
import { enregistrerDecisionFin } from "@/services/contrat.service";
import type { ContratResponse } from "@/types/contrat.types";

const Decisions = ["RENOUVELLEMENT", "CDI", "NON_RENOUVELE"] as const;

function buildSchema(t: ReturnType<typeof useTranslations>) {
  return z
    .object({
      decision: z.enum(Decisions),
      dateDecision: z.string().min(1, t("zodDateDecision")),
      commentaire: z.string().optional(),
    })
    .superRefine((val, ctx) => {
      if (val.decision === "NON_RENOUVELE") {
        const c = (val.commentaire ?? "").trim();
        if (!c) ctx.addIssue({ code: "custom", path: ["commentaire"], message: t("zodCommentNon") });
      }
    });
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>;

export function DecisionFinModal({
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
  const t = useTranslations("RH.contrats.decisionFin");

  const resolver = useMemo(() => zodResolver(buildSchema(t)), [t]);

  const form = useForm<FormValues>({
    resolver,
    defaultValues: {
      decision: "RENOUVELLEMENT",
      dateDecision: new Date().toISOString().slice(0, 10),
      commentaire: "",
    },
  });

  const decision = form.watch("decision");

  if (!contrat) return null;

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) {
          form.reset({
            decision: "RENOUVELLEMENT",
            dateDecision: new Date().toISOString().slice(0, 10),
            commentaire: "",
          });
        }
      }}
    >
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("subtitle", { nom: contrat.salarieNomComplet })}</DialogDescription>
        </DialogHeader>

        <form
          className="space-y-4"
          onSubmit={form.handleSubmit(async (vals) => {
            await enregistrerDecisionFin(contrat.id, {
              decision: vals.decision,
              dateDecision: vals.dateDecision,
              commentaire: vals.commentaire?.trim() ? vals.commentaire.trim() : null,
            });
            toast.success(t("toastOk"));
            onOpenChange(false);
            form.reset();
            onDone?.();
          })}
        >
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-foreground">{t("decisionLabel")}</legend>
            <div className="grid gap-2">
              {Decisions.map((d) => (
                <label key={d} className="flex items-center gap-2 text-sm">
                  <input type="radio" value={d} {...form.register("decision")} />
                  <span>{t(`decision.${d}` as const)}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <div className="space-y-1">
            <Label htmlFor="dateDecision">{t("dateDecision")}</Label>
            <Input id="dateDecision" type="date" {...form.register("dateDecision")} />
            {form.formState.errors.dateDecision?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.dateDecision.message)}</p>
            ) : null}
          </div>

          <div className="space-y-1">
            <Label htmlFor="commentaire">{t("commentaire")}</Label>
            <Textarea id="commentaire" rows={3} {...form.register("commentaire")} />
            {form.formState.errors.commentaire?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.commentaire.message)}</p>
            ) : null}
          </div>

          {decision === "NON_RENOUVELE" ? (
            <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-900 dark:border-red-900/40 dark:bg-red-950/30 dark:text-red-100">
              {t("warnNon")}
            </div>
          ) : null}

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
