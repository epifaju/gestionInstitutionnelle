"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { traiterEcheance } from "@/services/contrat.service";
import type { EcheanceResponse } from "@/types/contrat.types";

function schema(t: ReturnType<typeof useTranslations>) {
  return z.object({
    dateTraitement: z.string().min(1, t("zodDate")),
    commentaire: z.string().min(2, t("zodCommentaire")),
  });
}

type FormValues = z.infer<ReturnType<typeof schema>>;

export function TraiterEcheanceModal({
  open,
  onOpenChange,
  echeance,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  echeance: EcheanceResponse | null;
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.traiterEcheance");
  const resolver = useMemo(() => zodResolver(schema(t)), [t]);
  const [preuve, setPreuve] = useState<File | null>(null);

  const form = useForm<FormValues>({
    resolver,
    defaultValues: {
      dateTraitement: new Date().toISOString().slice(0, 10),
      commentaire: "",
    },
  });

  if (!echeance) return null;

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) {
          form.reset({ dateTraitement: new Date().toISOString().slice(0, 10), commentaire: "" });
          setPreuve(null);
        }
      }}
    >
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("subtitle")}</DialogDescription>
        </DialogHeader>

        <div className="mb-3 rounded-md border border-border bg-muted/40 p-3 text-sm">
          <div className="font-medium text-foreground">{echeance.titre}</div>
          <div className="text-muted-foreground">
            {t("readonly.type")}: {echeance.typeEcheance} · {t("readonly.date")}: {echeance.dateEcheance}
          </div>
        </div>

        <form
          className="space-y-3"
          onSubmit={form.handleSubmit(async (vals) => {
            await traiterEcheance(
              echeance.id,
              { dateTraitement: vals.dateTraitement, commentaire: vals.commentaire },
              preuve
            );
            toast.success(t("toastOk"));
            onOpenChange(false);
            form.reset({ dateTraitement: new Date().toISOString().slice(0, 10), commentaire: "" });
            setPreuve(null);
            onDone?.();
          })}
        >
          <div className="space-y-1">
            <Label htmlFor="dt">{t("dateTraitement")}</Label>
            <Input id="dt" type="date" {...form.register("dateTraitement")} />
            {form.formState.errors.dateTraitement?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.dateTraitement.message)}</p>
            ) : null}
          </div>

          <div className="space-y-1">
            <Label htmlFor="c">{t("commentaire")}</Label>
            <Textarea id="c" rows={4} {...form.register("commentaire")} />
            {form.formState.errors.commentaire?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.commentaire.message)}</p>
            ) : null}
          </div>

          <div className="space-y-1">
            <Label htmlFor="pj">{t("preuve")}</Label>
            <input id="pj" type="file" accept="application/pdf,image/*" onChange={(e) => setPreuve(e.target.files?.[0] ?? null)} />
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
