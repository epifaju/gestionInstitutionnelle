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
import { Textarea } from "@/components/ui/textarea";
import { listAdminUsers } from "@/services/admin.service";
import { creerEcheance } from "@/services/contrat.service";
import { listSalaries } from "@/services/salarie.service";

const TYPES = [
  "FIN_CDD",
  "FIN_PERIODE_ESSAI",
  "RENOUVELLEMENT_CDD",
  "VISITE_MEDICALE",
  "TITRE_SEJOUR",
  "FORMATION_OBLIGATOIRE",
  "AVENANT_CONTRAT",
  "AUTRE",
] as const;

const TypeEcheanceEnum = z.enum(TYPES);

function schema(t: ReturnType<typeof useTranslations>) {
  return z.object({
    salarieId: z.string().uuid(t("zodSalarie")),
    typeEcheance: TypeEcheanceEnum,
    titre: z.string().min(2, t("zodTitre")),
    description: z.string().optional(),
    dateEcheance: z.string().min(1, t("zodDate")),
    priorite: z.enum(["1", "2", "3"]),
    responsableId: z.string().optional(),
  });
}

type FormValues = z.infer<ReturnType<typeof schema>>;

export function EcheanceModal({
  open,
  onOpenChange,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.echeanceModal");

  const resolver = useMemo(() => zodResolver(schema(t)), [t]);
  const form = useForm<FormValues>({
    resolver,
    defaultValues: {
      salarieId: "",
      typeEcheance: "AUTRE",
      titre: "",
      description: "",
      dateEcheance: "",
      priorite: "2",
      responsableId: "",
    },
  });

  const [q, setQ] = useState("");
  const [debounced, setDebounced] = useState("");

  useEffect(() => {
    const h = window.setTimeout(() => setDebounced(q), 250);
    return () => window.clearTimeout(h);
  }, [q]);

  const { data: salariesPage } = useQuery({
    queryKey: ["rh", "salaries", "pick", debounced],
    queryFn: () => listSalaries({ page: 0, size: 25, search: debounced || undefined }),
    enabled: open,
  });

  const { data: usersPage } = useQuery({
    queryKey: ["admin", "users", "rh-pick"],
    queryFn: () => listAdminUsers({ page: 0, size: 200 }),
    enabled: open,
  });

  const responsables =
    usersPage?.content.filter((u) => u.actif && (u.role === "RH" || u.role === "ADMIN")) ?? [];

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) {
          form.reset({
            salarieId: "",
            typeEcheance: "AUTRE",
            titre: "",
            description: "",
            dateEcheance: "",
            priorite: "2",
            responsableId: "",
          });
          setQ("");
        }
      }}
    >
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("subtitle")}</DialogDescription>
        </DialogHeader>

        <form
          className="grid gap-3 md:grid-cols-2"
          onSubmit={form.handleSubmit(async (vals) => {
            await creerEcheance({
              salarieId: vals.salarieId,
              typeEcheance: vals.typeEcheance,
              titre: vals.titre,
              description: vals.description?.trim() ? vals.description.trim() : null,
              dateEcheance: vals.dateEcheance,
              priorite: Number(vals.priorite),
              responsableId: vals.responsableId ? vals.responsableId : null,
            });
            toast.success(t("toastOk"));
            onOpenChange(false);
            form.reset();
            onDone?.();
          })}
        >
          <div className="md:col-span-2 space-y-2">
            <Label>{t("salarie")}</Label>
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t("salarieSearch")} />
            <select className="h-9 w-full rounded-lg border border-input bg-transparent px-2 text-sm" {...form.register("salarieId")}>
              <option value="">{t("salariePlaceholder")}</option>
              {(salariesPage?.content ?? []).map((s) => (
                <option key={s.id} value={s.id}>
                  {s.prenom} {s.nom} · {s.matricule} · {s.service}
                </option>
              ))}
            </select>
            {form.formState.errors.salarieId?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.salarieId.message)}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="type">{t("type")}</Label>
            <select id="type" className="h-9 w-full rounded-lg border border-input bg-transparent px-2 text-sm" {...form.register("typeEcheance")}>
              {TYPES.map((x) => (
                <option key={x} value={x}>
                  {t(`types.${x}` as const)}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="date">{t("date")}</Label>
            <Input id="date" type="date" {...form.register("dateEcheance")} />
            {form.formState.errors.dateEcheance?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.dateEcheance.message)}</p>
            ) : null}
          </div>

          <div className="md:col-span-2 space-y-2">
            <Label htmlFor="titre">{t("titre")}</Label>
            <Input id="titre" {...form.register("titre")} />
            {form.formState.errors.titre?.message ? (
              <p className="text-sm text-red-600">{String(form.formState.errors.titre.message)}</p>
            ) : null}
          </div>

          <div className="md:col-span-2 space-y-2">
            <Label htmlFor="desc">{t("description")}</Label>
            <Textarea id="desc" rows={3} {...form.register("description")} />
          </div>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-foreground">{t("priorite")}</legend>
            <div className="flex flex-col gap-2 text-sm">
              <label className="flex items-center gap-2">
                <input type="radio" value="1" {...form.register("priorite")} />
                {t("prio.haute")}
              </label>
              <label className="flex items-center gap-2">
                <input type="radio" value="2" {...form.register("priorite")} />
                {t("prio.normale")}
              </label>
              <label className="flex items-center gap-2">
                <input type="radio" value="3" {...form.register("priorite")} />
                {t("prio.basse")}
              </label>
            </div>
          </fieldset>

          <div className="space-y-2">
            <Label htmlFor="resp">{t("responsable")}</Label>
            <select id="resp" className="h-9 w-full rounded-lg border border-input bg-transparent px-2 text-sm" {...form.register("responsableId")}>
              <option value="">{t("responsableNone")}</option>
              {responsables.map((u) => (
                <option key={u.id} value={u.id}>
                  {(u.prenom || "") + " " + (u.nom || "")} · {u.email} · {u.role}
                </option>
              ))}
            </select>
          </div>

          <div className="md:col-span-2 flex justify-end gap-2 pt-2">
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
