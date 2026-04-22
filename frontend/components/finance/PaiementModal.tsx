"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { FactureResponse, PaiementRequest } from "@/lib/types/finance";

export function PaiementModal({
  open,
  onClose,
  facture,
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  facture: FactureResponse | null;
  onSubmit: (body: PaiementRequest) => Promise<void>;
}) {
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [moyen, setMoyen] = useState("VIREMENT");
  const [compte, setCompte] = useState("");
  const [montant, setMontant] = useState("");
  const [notes, setNotes] = useState("");
  const [err, setErr] = useState<string | null>(null);

  if (!open || !facture) return null;

  const restant = parseFloat(String(facture.montantRestant));
  const devise = facture.devise;

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-md rounded-lg bg-white p-4 shadow-xl">
        <h3 className="mb-2 font-semibold">Paiement — {facture.reference}</h3>
        <p className="mb-3 text-sm text-slate-600">Restant : {restant.toFixed(2)} {devise}</p>
        {err && <p className="mb-2 text-sm text-red-600">{err}</p>}
        <div className="space-y-2">
          <div>
            <Label>Date</Label>
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div>
            <Label>Moyen</Label>
            <Input value={moyen} onChange={(e) => setMoyen(e.target.value)} />
          </div>
          <div>
            <Label>Compte</Label>
            <Input value={compte} onChange={(e) => setCompte(e.target.value)} />
          </div>
          <div>
            <Label>Montant ({devise})</Label>
            <Input
              type="number"
              step="0.01"
              value={montant}
              onChange={(e) => setMontant(e.target.value)}
            />
          </div>
          <div>
            <Label>Notes</Label>
            <Input value={notes} onChange={(e) => setNotes(e.target.value)} />
          </div>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Annuler
          </Button>
          <Button
            type="button"
            onClick={async () => {
              setErr(null);
              const m = parseFloat(montant);
              if (Number.isNaN(m) || m <= 0) {
                setErr("Montant invalide");
                return;
              }
              if (m > restant + 0.0001) {
                setErr("Montant supérieur au restant");
                return;
              }
              const body: PaiementRequest = {
                datePaiement: date,
                montantTotal: m,
                devise,
                compte: compte || null,
                moyenPaiement: moyen,
                factures: [{ factureId: facture.id, montant: m }],
                notes: notes || null,
              };
              try {
                await onSubmit(body);
                onClose();
              } catch (e: unknown) {
                setErr(e instanceof Error ? e.message : "Erreur");
              }
            }}
          >
            Enregistrer
          </Button>
        </div>
      </div>
    </div>
  );
}
