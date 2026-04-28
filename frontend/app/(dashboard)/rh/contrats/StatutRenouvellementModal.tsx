"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { mettreAJourStatutRenouvellementTitre } from "@/services/contrat.service";
import type { TitreSejourResponse } from "@/types/contrat.types";

const STATUTS = ["NON_ENGAGE", "EN_COURS", "DEPOSE", "OBTENU", "REFUSE"] as const;

export function StatutRenouvellementModal({
  open,
  onOpenChange,
  titre,
  onDone,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  titre: TitreSejourResponse | null;
  onDone?: () => void;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.statutTitre");
  const initial = useMemo(() => titre?.statutRenouvellement ?? "NON_ENGAGE", [titre]);
  const [statut, setStatut] = useState<string>(initial);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!open) return;
    setStatut(initial);
  }, [open, initial]);

  if (!titre) return null;

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) setStatut(initial);
      }}
    >
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("subtitle", { nom: titre.salarieNomComplet })}</DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="st">{t("statut")}</Label>
          <select
            id="st"
            className="h-9 w-full rounded-lg border border-input bg-transparent px-2 text-sm"
            value={statut}
            onChange={(e) => setStatut(e.target.value)}
          >
            {STATUTS.map((s) => (
              <option key={s} value={s}>
                {t(`options.${s}` as const)}
              </option>
            ))}
          </select>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
            {tc("close")}
          </Button>
          <Button
            type="button"
            disabled={pending}
            onClick={async () => {
              setPending(true);
              try {
                await mettreAJourStatutRenouvellementTitre(titre.id, statut);
                toast.success(t("toastOk"));
                onOpenChange(false);
                onDone?.();
              } finally {
                setPending(false);
              }
            }}
          >
            {pending ? tc("loading") : tc("save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
