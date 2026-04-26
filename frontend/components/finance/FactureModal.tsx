"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { DeviseSelector } from "@/components/ui/DeviseSelector";
import { convertirDevise } from "@/services/devises.service";
import type { CategorieResponse, FactureRequest, FactureResponse } from "@/lib/types/finance";

const schema = z.object({
  fournisseur: z.string().min(1),
  dateFacture: z.string().min(1),
  montantHt: z.number().positive(),
  tva: z.number().min(0),
  devise: z.string().length(3),
  categorieId: z.union([z.string().uuid(), z.literal("")]).optional(),
  statut: z.enum(["BROUILLON", "A_PAYER", "PAYE", "ANNULE"]),
  notes: z.string().optional().nullable(),
});

type FormValues = z.infer<typeof schema>;

function calcTtc(ht: number, tva: number) {
  return Math.round(ht * (1 + tva / 100) * 100) / 100;
}

export function FactureModal({
  open,
  onClose,
  categories,
  onSubmit,
  initialFacture,
}: {
  open: boolean;
  onClose: () => void;
  categories: CategorieResponse[];
  onSubmit: (data: FactureRequest, file: File | null) => Promise<void>;
  /** Si défini, formulaire prérempli (édition). */
  initialFacture?: FactureResponse | null;
}) {
  const t = useTranslations("Finance.factures.modal");
  const tc = useTranslations("Common");

  const [file, setFile] = useState<File | null>(null);
  const [convEur, setConvEur] = useState<number | null>(null);
  const [taux, setTaux] = useState<number | null>(null);
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fournisseur: "",
      dateFacture: new Date().toISOString().slice(0, 10),
      montantHt: 100,
      tva: 20,
      devise: "EUR",
      categorieId: "",
      statut: "BROUILLON",
      notes: "",
    },
  });
  const [ht, tva] = useWatch({ control: form.control, name: ["montantHt", "tva"] });
  const devise = useWatch({ control: form.control, name: "devise" });
  const dateFacture = useWatch({ control: form.control, name: "dateFacture" });
  const ttc = calcTtc(Number(ht) || 0, Number(tva) || 0);
  const isEdit = Boolean(initialFacture);

  useEffect(() => {
    if (!open) {
      form.reset();
      setFile(null);
      setConvEur(null);
      setTaux(null);
    }
  }, [open, form]);

  useEffect(() => {
    if (!open || !initialFacture) return;
    form.reset({
      fournisseur: initialFacture.fournisseur,
      dateFacture: initialFacture.dateFacture.slice(0, 10),
      montantHt: parseFloat(String(initialFacture.montantHt)),
      tva: parseFloat(String(initialFacture.tva)),
      devise: String(initialFacture.devise).toUpperCase(),
      categorieId: initialFacture.categorieId ?? "",
      statut: initialFacture.statut as FormValues["statut"],
      notes: "",
    });
    setFile(null);
    setConvEur(null);
    setTaux(null);
  }, [open, initialFacture, form]);

  useEffect(() => {
    if (!open || initialFacture) return;
    form.reset({
      fournisseur: "",
      dateFacture: new Date().toISOString().slice(0, 10),
      montantHt: 100,
      tva: 20,
      devise: "EUR",
      categorieId: "",
      statut: "BROUILLON",
      notes: "",
    });
    setFile(null);
    setConvEur(null);
    setTaux(null);
  }, [open, initialFacture, form]);

  useEffect(() => {
    if (!open) return;
    const d = String(devise ?? "EUR").toUpperCase();
    if (d === "EUR") {
      setConvEur(null);
      setTaux(null);
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        const res = await convertirDevise({
          montant: ttc,
          de: d,
          vers: "EUR",
          date: dateFacture || undefined,
        });
        setConvEur(res.resultat);
        setTaux(res.taux);
      } catch {
        setConvEur(null);
        setTaux(null);
      }
    }, 500);
    return () => window.clearTimeout(timer);
  }, [open, devise, ttc, dateFacture]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg bg-card p-4 text-card-foreground shadow-xl">
        <div className="mb-3 flex justify-between">
          <h2 className="text-lg font-semibold">{isEdit ? t("titleEdit") : t("titleNew")}</h2>
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            {tc("close")}
          </Button>
        </div>
        <form
          className="space-y-3"
          onSubmit={form.handleSubmit(async (v) => {
            const req: FactureRequest = {
              fournisseur: v.fournisseur,
              dateFacture: v.dateFacture,
              montantHt: v.montantHt,
              tva: v.tva,
              devise: v.devise.toUpperCase(),
              categorieId: v.categorieId && v.categorieId.length > 0 ? v.categorieId : null,
              statut: v.statut,
              notes: v.notes || null,
            };
            await onSubmit(req, file);
            onClose();
          })}
        >
          <div>
            <Label>{t("supplier")}</Label>
            <Input {...form.register("fournisseur")} />
          </div>
          <div>
            <Label>{t("dateInvoice")}</Label>
            <Input type="date" {...form.register("dateFacture")} />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label>{t("amountHt")}</Label>
              <Input type="number" step="0.01" {...form.register("montantHt", { valueAsNumber: true })} />
            </div>
            <div>
              <Label>{t("vat")}</Label>
              <Input type="number" step="0.01" {...form.register("tva", { valueAsNumber: true })} />
            </div>
          </div>
          <p className="text-sm text-muted-foreground">
            {t("ttcCalculated")} : <span className="font-semibold text-foreground">{ttc.toFixed(2)}</span>
          </p>
          {String(devise ?? "EUR").toUpperCase() !== "EUR" ? (
            <p className="text-xs text-muted-foreground">
              ≈ <span className="font-medium text-foreground">{convEur != null ? convEur.toFixed(2) : "—"}</span> EUR{" "}
              {taux != null ? (
                <span className="text-muted-foreground">
                  (taux: {taux.toFixed(6)} · {dateFacture || "jour"})
                </span>
              ) : null}
            </p>
          ) : null}
          <div>
            <Label>{t("currency")}</Label>
            <DeviseSelector value={String(devise ?? "EUR")} onChange={(v) => form.setValue("devise", v)} />
          </div>
          <div>
            <Label>{t("category")}</Label>
            <select
              className="flex h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground"
              {...form.register("categorieId")}
            >
              <option value="">{tc("emDash")}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.libelle}
                </option>
              ))}
            </select>
            {categories.length === 0 ? (
              <p className="mt-1 text-xs text-muted-foreground">
                {t.rich("noCategoriesHint", { b: (chunks) => <span className="font-medium">{chunks}</span> })}
              </p>
            ) : null}
          </div>
          <div>
            <Label>{t("status")}</Label>
            <select className="flex h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground" {...form.register("statut")}>
              <option value="BROUILLON">{t("statusDraft")}</option>
              <option value="A_PAYER">{t("statusToPay")}</option>
              <option value="PAYE">{t("statusPaid")}</option>
              <option value="ANNULE">{t("statusCancelled")}</option>
            </select>
          </div>
          <div>
            <Label>{t("notes")}</Label>
            <Input {...form.register("notes")} />
          </div>
          <div>
            <Label>{isEdit ? t("receiptReplace") : t("receipt")}</Label>
            <Input
              type="file"
              accept="application/pdf,image/*"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            {file && <p className="text-xs text-muted-foreground">{file.name}</p>}
            {isEdit && !file ? (
              <p className="text-xs text-muted-foreground">{t("receiptKeepHint")}</p>
            ) : null}
          </div>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {tc("save")}
          </Button>
        </form>
      </div>
    </div>
  );
}
