"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { DeviseSelector } from "@/components/ui/DeviseSelector";
import { convertirDevise } from "@/services/devises.service";
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
  const t = useTranslations("Finance.recettes.modal");
  const tc = useTranslations("Common");

  const [file, setFile] = useState<File | null>(null);
  const [convEur, setConvEur] = useState<number | null>(null);
  const [taux, setTaux] = useState<number | null>(null);
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

  const resolvedTitle = title ?? t("titleNew");
  const resolvedSubmit = submitLabel ?? tc("confirm");
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
    setConvEur(null);
    setTaux(null);
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

  useEffect(() => {
    if (!open) return;
    const d = String(form.devise ?? "EUR").toUpperCase();
    if (d === "EUR") {
      setConvEur(null);
      setTaux(null);
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        const res = await convertirDevise({
          montant: Number(form.montant) || 0,
          de: d,
          vers: "EUR",
          date: form.dateRecette || undefined,
        });
        setConvEur(res.resultat);
        setTaux(res.taux);
      } catch {
        setConvEur(null);
        setTaux(null);
      }
    }, 500);
    return () => window.clearTimeout(timer);
  }, [open, form.montant, form.devise, form.dateRecette]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-md rounded-lg bg-white p-4 shadow-xl">
        <h2 className="mb-3 font-semibold">{resolvedTitle}</h2>
        <div className="space-y-2">
          <div>
            <Label>{t("date")}</Label>
            <Input
              type="date"
              value={form.dateRecette}
              onChange={(e) => setForm((f) => ({ ...f, dateRecette: e.target.value }))}
            />
          </div>
          <div>
            <Label>{t("type")}</Label>
            <select
              className="flex h-9 w-full rounded-md border border-slate-200 px-2 text-sm"
              value={form.typeRecette}
              onChange={(e) => setForm((f) => ({ ...f, typeRecette: e.target.value }))}
            >
              <option value="FRAIS_SERVICE">{t("type_FRAIS_SERVICE")}</option>
              <option value="ADHESION">{t("type_ADHESION")}</option>
              <option value="DON">{t("type_DON")}</option>
              <option value="SUBVENTION">{t("type_SUBVENTION")}</option>
              <option value="PRESTATION">{t("type_PRESTATION")}</option>
            </select>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label>{t("amount")}</Label>
              <Input
                type="number"
                value={form.montant}
                onChange={(e) => setForm((f) => ({ ...f, montant: parseFloat(e.target.value) || 0 }))}
              />
            </div>
            <div>
              <Label>{t("currency")}</Label>
              <DeviseSelector value={form.devise} onChange={(v) => setForm((f) => ({ ...f, devise: v }))} />
            </div>
          </div>
          {String(form.devise ?? "EUR").toUpperCase() !== "EUR" ? (
            <p className="text-xs text-slate-600">
              ≈ <span className="font-medium text-slate-900">{convEur != null ? convEur.toFixed(2) : "—"}</span> EUR{" "}
              {taux != null ? (
                <span className="text-slate-500">
                  (taux: {taux.toFixed(6)} · {form.dateRecette || "jour"})
                </span>
              ) : null}
            </p>
          ) : null}
          <div>
            <Label>{t("description")}</Label>
            <Input value={form.description ?? ""} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
          </div>
          <div>
            <Label>{t("paymentMode")}</Label>
            <Input
              value={form.modeEncaissement ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, modeEncaissement: e.target.value }))}
            />
          </div>
          <div>
            <Label>{t("category")}</Label>
            <select
              className="flex h-9 w-full rounded-md border border-slate-200 px-2 text-sm"
              value={form.categorieId ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, categorieId: e.target.value ? e.target.value : null }))}
            >
              <option value="">{tc("emDash")}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.libelle}
                </option>
              ))}
            </select>
            {categories.length === 0 ? (
              <p className="mt-1 text-xs text-slate-600">
                {t.rich("noCategoriesHint", { b: (chunks) => <span className="font-medium">{chunks}</span> })}
              </p>
            ) : null}
          </div>
          <div>
            <Label>{t("receipt")}</Label>
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
              <p className="text-xs text-slate-600">{t("receiptDisabled")}</p>
            )}
          </div>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            {tc("cancel")}
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
