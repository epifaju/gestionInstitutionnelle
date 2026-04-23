"use client";

import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { BienRequest, BienResponse } from "@/lib/types/inventaire";
import type { SalarieResponse } from "@/lib/types/rh";

const ETATS = ["BON", "USE", "DEFAILLANT", "HORS_SERVICE"] as const;

type Props = {
  open: boolean;
  onClose: () => void;
  onSubmit: (body: BienRequest) => void;
  pending?: boolean;
  initial?: BienResponse | null;
  salaries: SalarieResponse[];
};

export function BienModal({ open, onClose, onSubmit, pending, initial, salaries }: Props) {
  const t = useTranslations("Inventaire.modal");
  const tc = useTranslations("Common");
  const [libelle, setLibelle] = useState("");
  const [categorie, setCategorie] = useState("");
  const [codeCategorie, setCodeCategorie] = useState("");
  const [dateAcquisition, setDateAcquisition] = useState("");
  const [valeurAchat, setValeurAchat] = useState("");
  const [devise, setDevise] = useState("EUR");
  const [localisation, setLocalisation] = useState("");
  const [etat, setEtat] = useState<string>("BON");
  const [responsableId, setResponsableId] = useState("");
  const [description, setDescription] = useState("");

  useEffect(() => {
    if (!open) return;
    if (initial) {
      setLibelle(initial.libelle);
      setCategorie(initial.categorie);
      setCodeCategorie(initial.codeCategorie);
      setDateAcquisition(initial.dateAcquisition ?? "");
      setValeurAchat(String(initial.valeurAchat));
      setDevise(initial.devise);
      setLocalisation(initial.localisation ?? "");
      setEtat(initial.etat);
      setResponsableId(""); // resolved by nom only in list — user re-picks if needed
      setDescription("");
    } else {
      setLibelle("");
      setCategorie("");
      setCodeCategorie("");
      setDateAcquisition("");
      setValeurAchat("");
      setDevise("EUR");
      setLocalisation("");
      setEtat("BON");
      setResponsableId("");
      setDescription("");
    }
  }, [open, initial]);

  if (!open) return null;

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const body: BienRequest = {
      libelle,
      categorie,
      codeCategorie,
      dateAcquisition: dateAcquisition || null,
      valeurAchat: valeurAchat,
      devise: devise || "EUR",
      localisation: localisation || null,
      etat,
      responsableId: responsableId || null,
      description: description || null,
    };
    onSubmit(body);
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg bg-white p-6 shadow-lg">
        <h2 className="text-lg font-semibold">{initial ? t("titleEdit") : t("titleNew")}</h2>
        <form onSubmit={handleSubmit} className="mt-4 space-y-3">
          <div>
            <Label>{t("labelLibelle")}</Label>
            <Input value={libelle} onChange={(e) => setLibelle(e.target.value)} required />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label>{t("labelCategorie")}</Label>
              <Input value={categorie} onChange={(e) => setCategorie(e.target.value)} required />
            </div>
            <div>
              <Label>{t("labelCodeCategorie")}</Label>
              <Input value={codeCategorie} onChange={(e) => setCodeCategorie(e.target.value)} required />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label>{t("labelDateAcquisition")}</Label>
              <Input type="date" value={dateAcquisition} onChange={(e) => setDateAcquisition(e.target.value)} />
            </div>
            <div>
              <Label>{t("labelValeurAchat")}</Label>
              <Input
                inputMode="decimal"
                value={valeurAchat}
                onChange={(e) => setValeurAchat(e.target.value)}
                required
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label>{t("labelDevise")}</Label>
              <Input value={devise} onChange={(e) => setDevise(e.target.value)} />
            </div>
            <div>
              <Label>{t("labelEtat")}</Label>
              <select
                className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                value={etat}
                onChange={(e) => setEtat(e.target.value)}
              >
                {ETATS.map((x) => (
                  <option key={x} value={x}>
                    {t(`etat_${x}`)}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <Label>{t("labelLocalisation")}</Label>
            <Input value={localisation} onChange={(e) => setLocalisation(e.target.value)} />
          </div>
          <div>
            <Label>{t("labelResponsable")}</Label>
            <select
              className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              value={responsableId}
              onChange={(e) => setResponsableId(e.target.value)}
            >
              <option value="">{tc("emDash")}</option>
              {salaries.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.prenom} {s.nom}
                </option>
              ))}
            </select>
          </div>
          <div>
            <Label>{t("labelDescription")}</Label>
            <textarea
              className="flex min-h-[72px] w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={onClose}>
              {tc("cancel")}
            </Button>
            <Button type="submit" disabled={pending}>
              {tc("save")}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
