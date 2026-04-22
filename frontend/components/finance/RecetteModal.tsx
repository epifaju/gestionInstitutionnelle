"use client";

import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { CategorieResponse, RecetteRequest } from "@/lib/types/finance";

export function RecetteModal({
  open,
  onClose,
  categories,
  onSubmit,
  title,
  submitLabel,
  initial,
  allowFile,
}: {
  open: boolean;
  onClose: () => void;
  categories: CategorieResponse[];
  onSubmit: (req: RecetteRequest, file: File | null) => Promise<void>;
  title?: string;
  submitLabel?: string;
  initial?: Partial<RecetteRequest> | null;
  allowFile?: boolean;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [initKey, setInitKey] = useState<string>("");
  const [form, setForm] = useState<RecetteRequest>({
    dateRecette: new Date().toISOString().slice(0, 10),
    montant: 100,
    devise: "EUR",
    typeRecette: "DON",
    description: "",
    modeEncaissement: "",
    categorieId: null,
  });

  const resolvedTitle = title ?? "Nouvelle recette";
  const resolvedSubmit = submitLabel ?? "Créer";
  const canUpload = allowFile ?? true;

  const computedInitKey = useMemo(() => {
    if (!open) return "";
    if (!initial) return "open:create";
    return "open:" + JSON.stringify(initial);
  }, [open, initial]);

  useEffect(() => {
    if (!open) return;
    if (computedInitKey === initKey) return;
    setInitKey(computedInitKey);
    setFile(null);
    if (!initial) {
      setForm({
        dateRecette: new Date().toISOString().slice(0, 10),
        montant: 100,
        devise: "EUR",
        typeRecette: "DON",
        description: "",
        modeEncaissement: "",
        categorieId: null,
      });
      return;
    }
    setForm((f) => ({
      ...f,
      dateRecette: (initial.dateRecette as string) ?? f.dateRecette,
      montant:
        initial.montant === undefined || initial.montant === null
          ? f.montant
          : (initial.montant as number),
      devise: (initial.devise as string) ?? f.devise,
      typeRecette: (initial.typeRecette as string) ?? f.typeRecette,
      description: (initial.description as string | null) ?? f.description,
      modeEncaissement: (initial.modeEncaissement as string | null) ?? f.modeEncaissement,
      categorieId: (initial.categorieId as string | null) ?? f.categorieId,
    }));
  }, [open, initial, computedInitKey, initKey]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-md rounded-lg bg-white p-4 shadow-xl">
        <h2 className="mb-3 font-semibold">{resolvedTitle}</h2>
        <div className="space-y-2">
          <div>
            <Label>Date</Label>
            <Input
              type="date"
              value={form.dateRecette}
              onChange={(e) => setForm((f) => ({ ...f, dateRecette: e.target.value }))}
            />
          </div>
          <div>
            <Label>Type</Label>
            <select
              className="flex h-9 w-full rounded-md border border-slate-200 px-2 text-sm"
              value={form.typeRecette}
              onChange={(e) => setForm((f) => ({ ...f, typeRecette: e.target.value }))}
            >
              <option value="FRAIS_SERVICE">FRAIS_SERVICE</option>
              <option value="ADHESION">ADHESION</option>
              <option value="DON">DON</option>
              <option value="SUBVENTION">SUBVENTION</option>
              <option value="PRESTATION">PRESTATION</option>
            </select>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label>Montant</Label>
              <Input
                type="number"
                value={form.montant}
                onChange={(e) => setForm((f) => ({ ...f, montant: parseFloat(e.target.value) || 0 }))}
              />
            </div>
            <div>
              <Label>Devise</Label>
              <Input value={form.devise} maxLength={3} onChange={(e) => setForm((f) => ({ ...f, devise: e.target.value }))} />
            </div>
          </div>
          <div>
            <Label>Description</Label>
            <Input value={form.description ?? ""} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
          </div>
          <div>
            <Label>Mode encaissement</Label>
            <Input
              value={form.modeEncaissement ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, modeEncaissement: e.target.value }))}
            />
          </div>
          <div>
            <Label>Catégorie</Label>
            <select
              className="flex h-9 w-full rounded-md border border-slate-200 px-2 text-sm"
              value={form.categorieId ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, categorieId: e.target.value ? e.target.value : null }))}
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
            <Label>Justificatif</Label>
            {canUpload ? (
              <>
                <Input
                  type="file"
                  accept="application/pdf,image/*"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
                {file && <p className="text-xs text-slate-600">{file.name}</p>}
              </>
            ) : (
              <p className="text-xs text-slate-600">Le justificatif ne peut pas être modifié ici.</p>
            )}
          </div>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Annuler
          </Button>
          <Button
            type="button"
            onClick={async () => {
              await onSubmit(form, file);
              onClose();
            }}
          >
            {resolvedSubmit}
          </Button>
        </div>
      </div>
    </div>
  );
}
