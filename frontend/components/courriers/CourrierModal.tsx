"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type {
  CanalCourrier,
  CourrierRequest,
  CourrierResponse,
  SensCourrier,
  StatutCourrier,
} from "@/lib/types/courriers";

const SENS: SensCourrier[] = ["ENTREE", "SORTIE"];
const STATUTS: StatutCourrier[] = ["BROUILLON", "ENREGISTRE", "ENVOYE", "RECU", "CLASSE", "ANNULE"];
const CANAUX: CanalCourrier[] = ["COURRIER_POSTAL", "EMAIL", "MAIN_PROPRE", "AUTRE"];

type FormState = {
  sens: SensCourrier;
  statut: StatutCourrier;
  reference: string;
  objet: string;
  expediteur: string;
  destinataire: string;
  dateCourrier: string;
  dateReception: string;
  dateEnvoi: string;
  canal: CanalCourrier;
  notes: string;
};

const empty = (): FormState => ({
  sens: "ENTREE",
  statut: "BROUILLON",
  reference: "",
  objet: "",
  expediteur: "",
  destinataire: "",
  dateCourrier: new Date().toISOString().slice(0, 10),
  dateReception: "",
  dateEnvoi: "",
  canal: "COURRIER_POSTAL",
  notes: "",
});

export function CourrierModal({
  open,
  onClose,
  onSubmit,
  initial,
  canWrite,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: CourrierRequest, file: File | null) => Promise<void>;
  initial?: CourrierResponse | null;
  canWrite: boolean;
}) {
  const t = useTranslations("Courriers");
  const tc = useTranslations("Common");
  const [form, setForm] = useState<FormState>(empty());
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      setForm(empty());
      setFile(null);
      setSaving(false);
      return;
    }
    if (initial) {
      setForm({
        sens: initial.sens,
        statut: initial.statut,
        reference: initial.reference ?? "",
        objet: initial.objet,
        expediteur: initial.expediteur ?? "",
        destinataire: initial.destinataire ?? "",
        dateCourrier: initial.dateCourrier?.slice(0, 10) ?? "",
        dateReception: initial.dateReception?.slice(0, 10) ?? "",
        dateEnvoi: initial.dateEnvoi?.slice(0, 10) ?? "",
        canal: initial.canal,
        notes: initial.notes ?? "",
      });
    } else {
      setForm(empty());
    }
    setFile(null);
  }, [open, initial]);

  if (!open) return null;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite || !form.objet.trim() || !form.dateCourrier) return;
    setSaving(true);
    try {
      await onSubmit(
        {
          sens: form.sens,
          statut: form.statut,
          reference: form.reference.trim() || null,
          objet: form.objet.trim(),
          expediteur: form.expediteur.trim() || null,
          destinataire: form.destinataire.trim() || null,
          dateCourrier: form.dateCourrier,
          dateReception: form.dateReception || null,
          dateEnvoi: form.dateEnvoi || null,
          canal: form.canal,
          notes: form.notes.trim() || null,
          documentId: initial?.documentId ?? null,
        },
        file
      );
      onClose();
    } finally {
      setSaving(false);
    }
  }

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg border bg-background p-4 shadow-lg">
        <h2 className="text-lg font-semibold">{initial ? t("modal.editTitle") : t("modal.createTitle")}</h2>
        <p className="mb-4 text-sm text-muted-foreground">{t("modal.subtitle")}</p>
        <form className="space-y-3" onSubmit={handleSubmit}>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>{t("fields.sens")}</Label>
              <select
                className="mt-1 flex h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm"
                value={form.sens}
                disabled={!canWrite}
                onChange={(e) => set("sens", e.target.value as SensCourrier)}
              >
                {SENS.map((s) => (
                  <option key={s} value={s}>
                    {t(`sens.${s}`)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label>{t("fields.statut")}</Label>
              <select
                className="mt-1 flex h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm"
                value={form.statut}
                disabled={!canWrite}
                onChange={(e) => set("statut", e.target.value as StatutCourrier)}
              >
                {STATUTS.map((s) => (
                  <option key={s} value={s}>
                    {t(`statut.${s}`)}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <Label>{t("fields.objet")}</Label>
            <Input
              className="mt-1"
              value={form.objet}
              disabled={!canWrite}
              onChange={(e) => set("objet", e.target.value)}
              required
            />
          </div>

          <div>
            <Label>{t("fields.reference")}</Label>
            <Input
              className="mt-1"
              value={form.reference}
              disabled={!canWrite}
              onChange={(e) => set("reference", e.target.value)}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>{t("fields.expediteur")}</Label>
              <Input
                className="mt-1"
                value={form.expediteur}
                disabled={!canWrite}
                onChange={(e) => set("expediteur", e.target.value)}
              />
            </div>
            <div>
              <Label>{t("fields.destinataire")}</Label>
              <Input
                className="mt-1"
                value={form.destinataire}
                disabled={!canWrite}
                onChange={(e) => set("destinataire", e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>{t("fields.dateCourrier")}</Label>
              <Input
                type="date"
                className="mt-1"
                value={form.dateCourrier}
                disabled={!canWrite}
                onChange={(e) => set("dateCourrier", e.target.value)}
                required
              />
            </div>
            <div>
              <Label>{t("fields.canal")}</Label>
              <select
                className="mt-1 flex h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm"
                value={form.canal}
                disabled={!canWrite}
                onChange={(e) => set("canal", e.target.value as CanalCourrier)}
              >
                {CANAUX.map((c) => (
                  <option key={c} value={c}>
                    {t(`canal.${c}`)}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>{t("fields.dateReception")}</Label>
              <Input
                type="date"
                className="mt-1"
                value={form.dateReception}
                disabled={!canWrite}
                onChange={(e) => set("dateReception", e.target.value)}
              />
            </div>
            <div>
              <Label>{t("fields.dateEnvoi")}</Label>
              <Input
                type="date"
                className="mt-1"
                value={form.dateEnvoi}
                disabled={!canWrite}
                onChange={(e) => set("dateEnvoi", e.target.value)}
              />
            </div>
          </div>

          <div>
            <Label>{t("fields.notes")}</Label>
            <textarea
              className="mt-1 flex min-h-[72px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
              value={form.notes}
              disabled={!canWrite}
              onChange={(e) => set("notes", e.target.value)}
            />
          </div>

          {canWrite ? (
            <div>
              <Label>{t("fields.piece")}</Label>
              <Input
                type="file"
                className="mt-1"
                accept=".pdf,.doc,.docx,image/*"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
              {initial?.documentId ? (
                <p className="mt-1 text-xs text-muted-foreground">{t("modal.pieceExistante")}</p>
              ) : null}
            </div>
          ) : null}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={onClose}>
              {tc("cancel")}
            </Button>
            {canWrite ? (
              <Button type="submit" disabled={saving}>
                {saving ? tc("loading") : tc("save")}
              </Button>
            ) : null}
          </div>
        </form>
      </div>
    </div>
  );
}
