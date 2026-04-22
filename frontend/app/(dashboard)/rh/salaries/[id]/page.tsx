"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SalarieForm, type SalarieFormValues } from "@/components/forms/SalarieForm";
import { CongeForm } from "@/components/forms/CongeForm";
import { useAuthStore } from "@/lib/store";
import type { CongeRequest, MarquerPayeRequest, SalarieRequest } from "@/lib/types/rh";
import {
  ajouterGrilleSalariale,
  getDroitsConges,
  getSalarie,
  listDocuments,
  listHistoriqueSalaires,
  updateSalarie,
  uploadContrat,
  validerDossier,
} from "@/services/salarie.service";
import { listConges, rejeterConge, soumettreConge, validerConge } from "@/services/conge.service";
import { getPaieAnnuelle, marquerPaye } from "@/services/paie.service";

function statutVariant(s: string): "success" | "warning" | "muted" | "default" {
  if (s === "ACTIF") return "success";
  if (s === "EN_CONGE") return "warning";
  if (s === "SORTI") return "muted";
  return "default";
}

function paieVariant(s: string): "success" | "warning" | "default" {
  if (s === "PAYE") return "success";
  if (s === "EN_ATTENTE") return "warning";
  return "default";
}

function fmtMoney(v: string | number, devise: string) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency: devise || "EUR" }).format(n);
}

export default function SalarieDetailPage() {
  const params = useParams();
  const id = String(params.id);
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const isRh = user?.role === "RH";
  const isRhOrAdmin = user?.role === "RH" || user?.role === "ADMIN";
  const isRhOrFin = user?.role === "RH" || user?.role === "FINANCIER";
  const [tab, setTab] = useState<"infos" | "conges" | "paie" | "docs">("infos");
  const year = new Date().getFullYear();

  const { data: salarie, isLoading } = useQuery({
    queryKey: ["rh", "salarie", id],
    queryFn: () => getSalarie(id),
  });

  const { data: droits } = useQuery({
    queryKey: ["rh", "droits", id, year],
    queryFn: () => getDroitsConges(id, year),
    enabled: tab === "conges",
  });

  const { data: historique } = useQuery({
    queryKey: ["rh", "hist", id],
    queryFn: () => listHistoriqueSalaires(id),
    enabled: tab === "infos",
  });

  const { data: congesPage } = useQuery({
    queryKey: ["rh", "conges", id],
    queryFn: () => listConges({ salarieId: id, page: 0, size: 50 }),
    enabled: tab === "conges",
  });

  const { data: paiePage } = useQuery({
    queryKey: ["rh", "paie", id, year],
    queryFn: () => getPaieAnnuelle(id, year, { page: 0, size: 12 }),
    enabled: tab === "paie",
  });

  const { data: documents } = useQuery({
    queryKey: ["rh", "docs", id],
    queryFn: () => listDocuments(id),
    enabled: tab === "docs",
  });

  const mutValider = useMutation({
    mutationFn: () => validerDossier(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rh", "salarie", id] }),
  });

  const mutConge = useMutation({
    mutationFn: (b: CongeRequest) => soumettreConge(b),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rh", "conges", id] }),
  });

  const mutCongeValider = useMutation({
    mutationFn: (congeId: string) => validerConge(congeId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rh", "conges", id] }),
  });

  const mutCongeRejeter = useMutation({
    mutationFn: ({ congeId, motifRejet }: { congeId: string; motifRejet: string }) =>
      rejeterConge(congeId, { motifRejet }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "conges", id] });
      setRejectId(null);
      setMotif("");
    },
  });

  const mutUpload = useMutation({
    mutationFn: (f: File) => uploadContrat(id, f),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rh", "docs", id] }),
  });

  const mutPaie = useMutation({
    mutationFn: ({ pid, body }: { pid: string; body: MarquerPayeRequest }) => marquerPaye(pid, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rh", "paie", id, year] }),
  });

  const mutGrille = useMutation({
    mutationFn: (b: { brut: number; net: number; devise: string; dateDebut: string }) =>
      ajouterGrilleSalariale(id, b),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rh", "salarie", id] });
      qc.invalidateQueries({ queryKey: ["rh", "hist", id] });
    },
  });

  const [payOpen, setPayOpen] = useState<string | null>(null);
  const [payForm, setPayForm] = useState<MarquerPayeRequest>({
    datePaiement: new Date().toISOString().slice(0, 10),
    modePaiement: "VIREMENT",
    notes: "",
  });

  const [rejectId, setRejectId] = useState<string | null>(null);
  const [motif, setMotif] = useState("");
  const [confirmValidateCongeId, setConfirmValidateCongeId] = useState<string | null>(null);

  if (isLoading || !salarie) {
    return <p className="p-4 text-slate-600">Chargement…</p>;
  }

  const droitNum = droits ? parseFloat(String(droits.joursDroit)) : 0;
  const prisNum = droits ? parseFloat(String(droits.joursPris)) : 0;
  const pct = droitNum > 0 ? Math.min(100, Math.round((prisNum / droitNum) * 100)) : 0;

  const defaultEdit: Partial<SalarieFormValues> = {
    nom: salarie.nom,
    prenom: salarie.prenom,
    email: salarie.email ?? "",
    telephone: salarie.telephone ?? "",
    poste: salarie.poste,
    service: salarie.service,
    dateEmbauche: salarie.dateEmbauche,
    typeContrat: salarie.typeContrat,
    nationalite: salarie.nationalite ?? "",
    adresse: salarie.adresse ?? "",
    montantBrut: salarie.salaireActuel ? parseFloat(String(salarie.salaireActuel.montantBrut)) : 3000,
    montantNet: salarie.salaireActuel ? parseFloat(String(salarie.salaireActuel.montantNet)) : 2400,
    devise: salarie.salaireActuel?.devise ?? "EUR",
  };

  async function onUpdate(req: SalarieRequest) {
    await updateSalarie(id, req);
    qc.invalidateQueries({ queryKey: ["rh", "salarie", id] });
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Link href="/rh/salaries" className="text-sm text-indigo-600 hover:underline">
          ← Salariés
        </Link>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">
            {salarie.prenom} {salarie.nom}
          </h1>
          <p className="text-sm text-slate-600">
            {salarie.matricule} · {salarie.service}
          </p>
        </div>
        <Badge variant={statutVariant(salarie.statut)}>{salarie.statut}</Badge>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-2">
        {(["infos", "conges", "paie", "docs"] as const).map((t) => (
          <Button key={t} type="button" variant={tab === t ? "default" : "outline"} size="sm" onClick={() => setTab(t)}>
            {t === "infos" && "Infos"}
            {t === "conges" && "Congés"}
            {t === "paie" && "Paie"}
            {t === "docs" && "Documents"}
          </Button>
        ))}
      </div>

      {tab === "infos" && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card className="p-4">
            <h2 className="mb-3 font-semibold text-slate-900">Fiche</h2>
            <dl className="grid grid-cols-1 gap-2 text-sm md:grid-cols-2">
              <div>
                <dt className="text-slate-500">Email</dt>
                <dd>{salarie.email ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Téléphone</dt>
                <dd>{salarie.telephone ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Poste</dt>
                <dd>{salarie.poste}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Contrat</dt>
                <dd>{salarie.typeContrat}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Embauche</dt>
                <dd>{salarie.dateEmbauche}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Nationalité</dt>
                <dd>{salarie.nationalite ?? "—"}</dd>
              </div>
            </dl>
            {isRh && salarie.statut === "BROUILLON" && (
              <Button className="mt-4" type="button" onClick={() => mutValider.mutate()} disabled={mutValider.isPending}>
                Valider le dossier
              </Button>
            )}
          </Card>
          <Card className="p-4">
            <h2 className="mb-3 font-semibold text-slate-900">Modifier</h2>
            <SalarieForm defaultValues={defaultEdit} submitLabel="Enregistrer" onSubmit={onUpdate} salaireEditable={false} />
          </Card>
          <Card className="p-4 lg:col-span-2">
            <h2 className="mb-3 font-semibold text-slate-900">Historique salaires</h2>
            <div className="relative space-y-4 border-l-2 border-indigo-200 pl-4">
              {(historique ?? []).map((h, i) => (
                <div key={i} className="relative">
                  <span className="absolute -left-[21px] top-1 h-3 w-3 rounded-full bg-indigo-500" />
                  <p className="text-sm font-medium text-slate-900">
                    {fmtMoney(h.montantNet, h.devise)} net ({fmtMoney(h.montantBrut, h.devise)} brut)
                  </p>
                  <p className="text-xs text-slate-600">
                    {h.dateDebut}
                    {h.dateFin ? ` → ${h.dateFin}` : " (en cours)"}
                  </p>
                </div>
              ))}
            </div>
            <div className="mt-6 border-t border-slate-100 pt-4">
              <h3 className="mb-2 text-sm font-semibold text-slate-800">Nouvelle grille</h3>
              <GrilleInline
                onSubmit={async (b) => {
                  await mutGrille.mutateAsync({
                    brut: b.brut,
                    net: b.net,
                    devise: b.devise,
                    dateDebut: b.dateDebut,
                  });
                }}
              />
            </div>
          </Card>
        </div>
      )}

      {tab === "conges" && (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card className="p-4">
            <h2 className="mb-2 font-semibold text-slate-900">Solde {year}</h2>
            {droits && (
              <>
                <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full bg-indigo-500 transition-all" style={{ width: `${pct}%` }} />
                </div>
                <p className="mt-2 text-sm text-slate-600">
                  Pris : {droits.joursPris} / {droits.joursDroit} · Restants : {droits.joursRestants}
                </p>
              </>
            )}
            <div className="mt-6 border-t border-slate-100 pt-4">
              <h3 className="mb-2 text-sm font-semibold">Nouvelle demande</h3>
              <CongeForm
                salarieId={id}
                onSubmit={async (b) => {
                  await mutConge.mutateAsync(b);
                }}
              />
            </div>
          </Card>
          <Card className="p-4">
            <h2 className="mb-2 font-semibold text-slate-900">Demandes</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-slate-500">
                    <th className="py-1">Période</th>
                    <th>Type</th>
                    <th>Jours</th>
                    <th>Statut</th>
                    <th className="text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {(congesPage?.content ?? []).map((c) => (
                    <tr key={c.id} className="border-b border-slate-100">
                      <td className="py-1">
                        {c.dateDebut} → {c.dateFin}
                      </td>
                      <td>{c.typeConge}</td>
                      <td>{c.nbJours}</td>
                      <td>
                        <Badge variant="default">{c.statut}</Badge>
                      </td>
                      <td className="text-right">
                        {isRhOrAdmin && c.statut === "EN_ATTENTE" ? (
                          <div className="inline-flex items-center justify-end gap-2">
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              disabled={mutCongeValider.isPending}
                              onClick={() => setConfirmValidateCongeId(c.id)}
                            >
                              Valider
                            </Button>
                            <Button type="button" size="sm" variant="outline" onClick={() => setRejectId(c.id)}>
                              Rejeter
                            </Button>
                          </div>
                        ) : (
                          <span className="text-slate-400">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      )}

      {tab === "paie" && (
        <Card className="p-4">
          <h2 className="mb-3 font-semibold text-slate-900">Paie {year}</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-slate-500">
                  <th className="py-1">Mois</th>
                  <th>Montant</th>
                  <th>Statut</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {(paiePage?.content ?? []).map((p) => (
                  <tr key={p.id} className="border-b border-slate-100">
                    <td className="py-1">{p.mois}</td>
                    <td>{fmtMoney(p.montant, p.devise)}</td>
                    <td>
                      <Badge variant={paieVariant(p.statut)}>{p.statut}</Badge>
                    </td>
                    <td className="text-right">
                      {p.statut === "EN_ATTENTE" && isRhOrFin && (
                        <Button type="button" size="sm" variant="outline" onClick={() => setPayOpen(p.id)}>
                          Marquer payé
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {tab === "docs" && (
        <Card className="p-4">
          <h2 className="mb-3 font-semibold text-slate-900">Contrats (PDF)</h2>
          <label className="flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-600 hover:bg-slate-100">
            <input
              type="file"
              accept="application/pdf"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) mutUpload.mutate(f);
                e.target.value = "";
              }}
            />
            Glisser-déposer ou cliquer pour envoyer un PDF (max 10 Mo)
          </label>
          <ul className="mt-4 space-y-2">
            {(documents ?? []).map((d) => (
              <li key={d.url} className="flex items-center justify-between text-sm">
                <span>{d.nomFichier}</span>
                <a className="text-indigo-600 hover:underline" href={d.url} target="_blank" rel="noreferrer">
                  Ouvrir
                </a>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {payOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <Card className="w-full max-w-md p-4">
            <h3 className="mb-3 font-semibold">Marquer comme payé</h3>
            <div className="space-y-2">
              <div>
                <Label>Date paiement</Label>
                <Input
                  type="date"
                  value={payForm.datePaiement}
                  onChange={(e) => setPayForm((p) => ({ ...p, datePaiement: e.target.value }))}
                />
              </div>
              <div>
                <Label>Mode</Label>
                <Input
                  value={payForm.modePaiement}
                  onChange={(e) => setPayForm((p) => ({ ...p, modePaiement: e.target.value }))}
                />
              </div>
              <div>
                <Label>Notes</Label>
                <Input
                  value={payForm.notes ?? ""}
                  onChange={(e) => setPayForm((p) => ({ ...p, notes: e.target.value }))}
                />
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setPayOpen(null)}>
                Annuler
              </Button>
              <Button
                type="button"
                onClick={() =>
                  mutPaie.mutate(
                    { pid: payOpen, body: payForm },
                    { onSuccess: () => setPayOpen(null) }
                  )
                }
              >
                Confirmer
              </Button>
            </div>
          </Card>
        </div>
      )}

      {rejectId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <Card className="w-full max-w-md p-4">
            <h3 className="mb-1 font-semibold">Rejeter la demande</h3>
            <p className="mb-2 text-xs text-slate-600">Confirmez le rejet en indiquant un motif.</p>
            <Input value={motif} onChange={(e) => setMotif(e.target.value)} placeholder="Obligatoire" />
            <div className="mt-3 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setRejectId(null)}>
                Annuler
              </Button>
              <Button
                type="button"
                disabled={!motif.trim() || mutCongeRejeter.isPending}
                onClick={() => mutCongeRejeter.mutate({ congeId: rejectId, motifRejet: motif.trim() })}
              >
                Confirmer le rejet
              </Button>
            </div>
          </Card>
        </div>
      )}

      {confirmValidateCongeId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <Card className="w-full max-w-md p-4">
            <h3 className="mb-1 font-semibold">Valider la demande</h3>
            <p className="mb-3 text-xs text-slate-600">Cette action passera la demande au statut VALIDE.</p>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setConfirmValidateCongeId(null)}>
                Annuler
              </Button>
              <Button
                type="button"
                disabled={mutCongeValider.isPending}
                onClick={() =>
                  mutCongeValider.mutate(confirmValidateCongeId, { onSuccess: () => setConfirmValidateCongeId(null) })
                }
              >
                Confirmer la validation
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}

function GrilleInline({
  onSubmit,
}: {
  onSubmit: (b: { brut: number; net: number; devise: string; dateDebut: string }) => Promise<void>;
}) {
  const [brut, setBrut] = useState("3000");
  const [net, setNet] = useState("2400");
  const [devise, setDevise] = useState("EUR");
  const [dateDebut, setDateDebut] = useState(new Date().toISOString().slice(0, 10));
  const [loading, setLoading] = useState(false);
  return (
    <form
      className="grid gap-2 md:grid-cols-4"
      onSubmit={async (e) => {
        e.preventDefault();
        setLoading(true);
        try {
          await onSubmit({
            brut: parseFloat(brut),
            net: parseFloat(net),
            devise,
            dateDebut,
          });
        } finally {
          setLoading(false);
        }
      }}
    >
      <div>
        <Label>Brut</Label>
        <Input value={brut} onChange={(e) => setBrut(e.target.value)} />
      </div>
      <div>
        <Label>Net</Label>
        <Input value={net} onChange={(e) => setNet(e.target.value)} />
      </div>
      <div>
        <Label>Devise</Label>
        <Input value={devise} onChange={(e) => setDevise(e.target.value)} maxLength={3} />
      </div>
      <div>
        <Label>Depuis</Label>
        <Input type="date" value={dateDebut} onChange={(e) => setDateDebut(e.target.value)} />
      </div>
      <div className="md:col-span-4">
        <Button type="submit" disabled={loading}>
          Ajouter la grille
        </Button>
      </div>
    </form>
  );
}
