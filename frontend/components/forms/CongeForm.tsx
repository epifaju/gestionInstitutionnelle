"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { compterJoursOuvres } from "@/lib/jours-ouvres";
import type { CongeRequest } from "@/lib/types/rh";

const schema = z.object({
  salarieId: z.string().uuid(),
  typeConge: z.enum(["ANNUEL", "MALADIE", "EXCEPTIONNEL", "SANS_SOLDE"]),
  dateDebut: z.string().min(1),
  dateFin: z.string().min(1),
  commentaire: z.string().optional(),
});

export type CongeFormValues = z.infer<typeof schema>;

const TYPE_OPTIONS = ["ANNUEL", "MALADIE", "EXCEPTIONNEL", "SANS_SOLDE"] as const;

export function CongeForm({
  salarieId,
  onSubmit,
}: {
  salarieId: string;
  onSubmit: (data: CongeRequest) => Promise<void>;
}) {
  const t = useTranslations("RH.conges");
  const form = useForm<CongeFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      salarieId,
      typeConge: "ANNUEL",
      dateDebut: new Date().toISOString().slice(0, 10),
      dateFin: new Date().toISOString().slice(0, 10),
      commentaire: "",
    },
  });

  const dd = useWatch({ control: form.control, name: "dateDebut" });
  const df = useWatch({ control: form.control, name: "dateFin" });
  let nb = 0;
  if (dd && df) {
    nb = compterJoursOuvres(new Date(dd + "T12:00:00"), new Date(df + "T12:00:00"));
  }

  return (
    <form
      className="space-y-3"
      onSubmit={form.handleSubmit(async (v) => {
        await onSubmit({
          salarieId: v.salarieId,
          typeConge: v.typeConge,
          dateDebut: v.dateDebut,
          dateFin: v.dateFin,
          commentaire: v.commentaire || null,
        });
      })}
    >
      <input type="hidden" {...form.register("salarieId")} />
      <div>
        <Label>{t("form.typeLabel")}</Label>
        <select
          className="flex h-9 w-full rounded-md border border-border bg-background px-3 text-sm text-foreground"
          {...form.register("typeConge")}
        >
          {TYPE_OPTIONS.map((code) => (
            <option key={code} value={code}>
              {t(`type.${code}`)}
            </option>
          ))}
        </select>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <div>
          <Label htmlFor="dateDebut">{t("form.dateDebut")}</Label>
          <Input id="dateDebut" type="date" {...form.register("dateDebut")} />
        </div>
        <div>
          <Label htmlFor="dateFin">{t("form.dateFin")}</Label>
          <Input id="dateFin" type="date" {...form.register("dateFin")} />
        </div>
      </div>
      <p className="text-sm text-muted-foreground">
        {t("form.estimatedDays", { count: nb })}
      </p>
      <div>
        <Label htmlFor="commentaire">{t("form.commentaire")}</Label>
        <Input id="commentaire" {...form.register("commentaire")} />
      </div>
      <Button type="submit" disabled={form.formState.isSubmitting}>
        {t("form.submit")}
      </Button>
    </form>
  );
}
