"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
  const [file, setFile] = useState<File | null>(null);
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
  const ttc = calcTtc(Number(ht) || 0, Number(tva) || 0);
  const isEdit = Boolean(initialFacture);

  useEffect(() => {
    if (!open) {
      form.reset();
      setFile(null);
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
  }, [open, initialFacture, form]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg bg-white p-4 shadow-xl">
        <div className="mb-3 flex justify-between">
          <h2 className="text-lg font-semibold">{isEdit ? "Modifier la facture" : "Nouvelle facture"}</h2>
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            Fermer
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
            <Label>Fournisseur</Label>
            <Input {...form.register("fournisseur")} />
          </div>
          <div>
            <Label>Date facture</Label>
            <Input type="date" {...form.register("dateFacture")} />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label>Montant HT</Label>
              <Input type="number" step="0.01" {...form.register("montantHt", { valueAsNumber: true })} />
            </div>
            <div>
              <Label>TVA %</Label>
              <Input type="number" step="0.01" {...form.register("tva", { valueAsNumber: true })} />
            </div>
          </div>
          <p className="text-sm text-slate-600">
            TTC calculé : <span className="font-semibold text-slate-900">{ttc.toFixed(2)}</span>
          </p>
          <div>
            <Label>Devise</Label>
            <Input maxLength={3} {...form.register("devise")} />
          </div>
          <div>
            <Label>Catégorie</Label>
            <select
              className="flex h-9 w-full rounded-md border border-slate-200 px-2 text-sm"
              {...form.register("categorieId")}
            >
              <option value="">—</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.libelle}
                </option>
              ))}
            </select>
            {categories.length === 0 ? (
              <p className="mt-1 text-xs text-slate-600">
                Aucune catégorie. Créez-en dans <span className="font-medium">Finance → Catégories</span>.
              </p>
            ) : null}
          </div>
          <div>
            <Label>Statut</Label>
            <select className="flex h-9 w-full rounded-md border border-slate-200 px-2 text-sm" {...form.register("statut")}>
              <option value="BROUILLON">Brouillon</option>
              <option value="A_PAYER">À payer</option>
              <option value="PAYE">Payé</option>
              <option value="ANNULE">Annulé</option>
            </select>
          </div>
          <div>
            <Label>Notes</Label>
            <Input {...form.register("notes")} />
          </div>
          <div>
            <Label>{isEdit ? "Remplacer le justificatif (PDF ou image)" : "Justificatif (PDF ou image)"}</Label>
            <Input
              type="file"
              accept="application/pdf,image/*"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            {file && <p className="text-xs text-slate-600">{file.name}</p>}
            {isEdit && !file ? (
              <p className="text-xs text-slate-500">Laissez vide pour conserver le justificatif actuel.</p>
            ) : null}
          </div>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            Enregistrer
          </Button>
        </form>
      </div>
    </div>
  );
}
