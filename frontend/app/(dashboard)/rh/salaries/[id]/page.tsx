"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { intlLocaleTag } from "@/lib/intl-locale";
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

function fmtMoney(v: string | number, devise: string, localeTag: string) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return new Intl.NumberFormat(localeTag, { style: "currency", currency: devise || "EUR" }).format(n);
}

export default function SalarieDetailPage() {
  const ts = useTranslations("RH.salaries");
  const tdt = useTranslations("RH.salaries.detail");
  const tc = useTranslations("Common");
  const localeTag = intlLocaleTag(useLocale());
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
    return <p className="p-4 text-muted-foreground">{tc("loading")}</p>;
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
          {ts("backToList")}
        </Link>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">
            {salarie.prenom} {salarie.nom}
          </h1>
          <p className="text-sm text-muted-foreground">
            {salarie.matricule} · {salarie.service}
          </p>
        </div>
        <Badge variant={statutVariant(salarie.statut)}>{salarie.statut}</Badge>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-border pb-2">
        {(["infos", "conges", "paie", "docs"] as const).map((tabKey) => (
          <Button
            key={tabKey}
            type="button"
            variant={tab === tabKey ? "default" : "outline"}
            size="sm"
            onClick={() => setTab(tabKey)}
          >
            {tabKey === "infos" && ts("tabInfos")}
            {tabKey === "conges" && ts("tabConges")}
            {tabKey === "paie" && ts("tabPaie")}
            {tabKey === "docs" && ts("tabDocs")}
          </Button>
        ))}
      </div>

      {tab === "infos" && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card className="p-4">
            <h2 className="mb-3 font-semibold text-foreground">{ts("cardFiche")}</h2>
            <dl className="grid grid-cols-1 gap-2 text-sm md:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">{tdt("dtEmail")}</dt>
                <dd>{salarie.email ?? tc("emDash")}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{tdt("dtTelephone")}</dt>
                <dd>{salarie.telephone ?? tc("emDash")}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{tdt("dtPoste")}</dt>
                <dd>{salarie.poste}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{tdt("dtContrat")}</dt>
                <dd>{salarie.typeContrat}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{tdt("dtEmbauche")}</dt>
                <dd>{salarie.dateEmbauche}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{tdt("dtNationalite")}</dt>
                <dd>{salarie.nationalite ?? tc("emDash")}</dd>
              </div>
            </dl>
            {isRh && salarie.statut === "BROUILLON" && (
              <Button className="mt-4" type="button" onClick={() => mutValider.mutate()} disabled={mutValider.isPending}>
                {ts("validateDossier")}
              </Button>
            )}
          </Card>
          <Card className="p-4">
            <h2 className="mb-3 font-semibold text-foreground">{ts("cardEdit")}</h2>
            <SalarieForm defaultValues={defaultEdit} submitLabel={tc("save")} onSubmit={onUpdate} salaireEditable={false} />
          </Card>
          <Card className="p-4 lg:col-span-2">
            <h2 className="mb-3 font-semibold text-foreground">{ts("histSalaires")}</h2>
            <div className="relative space-y-4 border-l-2 border-indigo-200 pl-4">
              {(historique ?? []).map((h, i) => (
                <div key={i} className="relative">
                  <span className="absolute -left-[21px] top-1 h-3 w-3 rounded-full bg-indigo-500" />
                  <p className="text-sm font-medium text-foreground">
                    {ts("netBrut", {
                      net: fmtMoney(h.montantNet, h.devise, localeTag),
                      brut: fmtMoney(h.montantBrut, h.devise, localeTag),
                    })}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {h.dateDebut}
                    {h.dateFin ? ` → ${h.dateFin}` : ` ${ts("ongoing")}`}
                  </p>
                </div>
              ))}
            </div>
            <div className="mt-6 border-t border-border pt-4">
              <h3 className="mb-2 text-sm font-semibold text-foreground">{ts("newGrille")}</h3>
              <GrilleInline
                labels={{
                  brut: ts("labelBrut"),
                  net: ts("labelNet"),
                  devise: ts("labelDevise"),
                  depuis: ts("labelDepuis"),
                  submit: ts("addGrille"),
                }}
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
            <h2 className="mb-2 font-semibold text-foreground">{ts("soldeYear", { year })}</h2>
            {droits && (
              <>
                <div className="h-3 w-full overflow-hidden rounded-full bg-muted">
                  <div className="h-full bg-indigo-500 transition-all" style={{ width: `${pct}%` }} />
                </div>
                <p className="mt-2 text-sm text-muted-foreground">
                  {ts("soldeTaken", { pris: droits.joursPris, droit: droits.joursDroit, restants: droits.joursRestants })}
                </p>
              </>
            )}
            <div className="mt-6 border-t border-border pt-4">
              <h3 className="mb-2 text-sm font-semibold">{ts("newRequest")}</h3>
              <CongeForm
                salarieId={id}
                onSubmit={async (b) => {
                  await mutConge.mutateAsync(b);
                }}
              />
            </div>
          </Card>
          <Card className="p-4">
            <h2 className="mb-2 font-semibold text-foreground">{ts("demandes")}</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-muted-foreground">
                    <th className="py-1">{ts("thPeriode")}</th>
                    <th>{ts("thType")}</th>
                    <th>{ts("thJours")}</th>
                    <th>{ts("thStatut")}</th>
                    <th className="text-right">{ts("thActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {(congesPage?.content ?? []).map((c) => (
                    <tr key={c.id} className="border-b border-border">
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
                              {tc("validate")}
                            </Button>
                            <Button type="button" size="sm" variant="outline" onClick={() => setRejectId(c.id)}>
                              {tc("reject")}
                            </Button>
                          </div>
                        ) : (
                          <span className="text-muted-foreground">{tc("emDash")}</span>
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
          <h2 className="mb-3 font-semibold text-foreground">{ts("paieYear", { year })}</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-muted-foreground">
                  <th className="py-1">{ts("thMois")}</th>
                  <th>{ts("thMontant")}</th>
                  <th>{ts("thStatut")}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {(paiePage?.content ?? []).map((p) => (
                  <tr key={p.id} className="border-b border-border">
                    <td className="py-1">{p.mois}</td>
                    <td>{fmtMoney(p.montant, p.devise, localeTag)}</td>
                    <td>
                      <Badge variant={paieVariant(p.statut)}>{p.statut}</Badge>
                    </td>
                    <td className="text-right">
                      {p.statut === "EN_ATTENTE" && isRhOrFin && (
                        <Button type="button" size="sm" variant="outline" onClick={() => setPayOpen(p.id)}>
                          {tc("markPaid")}
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
          <h2 className="mb-3 font-semibold text-foreground">{ts("contractsPdf")}</h2>
          <label className="flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-border bg-muted p-8 text-center text-sm text-muted-foreground hover:bg-muted/80">
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
            {ts("uploadPdfHint")}
          </label>
          <ul className="mt-4 space-y-2">
            {(documents ?? []).map((d) => (
              <li key={d.url} className="flex items-center justify-between text-sm">
                <span>{d.nomFichier}</span>
                <a className="text-indigo-600 hover:underline" href={d.url} target="_blank" rel="noreferrer">
                  {tc("open")}
                </a>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {payOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <Card className="w-full max-w-md p-4">
            <h3 className="mb-3 font-semibold">{ts("markPaidTitle")}</h3>
            <div className="space-y-2">
              <div>
                <Label>{ts("labelDatePaiement")}</Label>
                <Input
                  type="date"
                  value={payForm.datePaiement}
                  onChange={(e) => setPayForm((p) => ({ ...p, datePaiement: e.target.value }))}
                />
              </div>
              <div>
                <Label>{ts("labelMode")}</Label>
                <Input
                  value={payForm.modePaiement}
                  onChange={(e) => setPayForm((p) => ({ ...p, modePaiement: e.target.value }))}
                />
              </div>
              <div>
                <Label>{ts("labelNotes")}</Label>
                <Input
                  value={payForm.notes ?? ""}
                  onChange={(e) => setPayForm((p) => ({ ...p, notes: e.target.value }))}
                />
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setPayOpen(null)}>
                {tc("cancel")}
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
                {tc("confirm")}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {rejectId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <Card className="w-full max-w-md p-4">
            <h3 className="mb-1 font-semibold">{ts("rejectTitle")}</h3>
            <p className="mb-2 text-xs text-muted-foreground">{ts("rejectHint")}</p>
            <Input value={motif} onChange={(e) => setMotif(e.target.value)} placeholder={ts("placeholderRequired")} />
            <div className="mt-3 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setRejectId(null)}>
                {tc("cancel")}
              </Button>
              <Button
                type="button"
                disabled={!motif.trim() || mutCongeRejeter.isPending}
                onClick={() => mutCongeRejeter.mutate({ congeId: rejectId, motifRejet: motif.trim() })}
              >
                {ts("confirmReject")}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {confirmValidateCongeId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <Card className="w-full max-w-md p-4">
            <h3 className="mb-1 font-semibold">{ts("validateCongeTitle")}</h3>
            <p className="mb-3 text-xs text-muted-foreground">{ts("validateCongeHint")}</p>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setConfirmValidateCongeId(null)}>
                {tc("cancel")}
              </Button>
              <Button
                type="button"
                disabled={mutCongeValider.isPending}
                onClick={() =>
                  mutCongeValider.mutate(confirmValidateCongeId, { onSuccess: () => setConfirmValidateCongeId(null) })
                }
              >
                {ts("confirmValidation")}
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}

function GrilleInline({
  labels,
  onSubmit,
}: {
  labels: { brut: string; net: string; devise: string; depuis: string; submit: string };
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
        <Label>{labels.brut}</Label>
        <Input value={brut} onChange={(e) => setBrut(e.target.value)} />
      </div>
      <div>
        <Label>{labels.net}</Label>
        <Input value={net} onChange={(e) => setNet(e.target.value)} />
      </div>
      <div>
        <Label>{labels.devise}</Label>
        <Input value={devise} onChange={(e) => setDevise(e.target.value)} maxLength={3} />
      </div>
      <div>
        <Label>{labels.depuis}</Label>
        <Input type="date" value={dateDebut} onChange={(e) => setDateDebut(e.target.value)} />
      </div>
      <div className="md:col-span-4">
        <Button type="submit" disabled={loading}>
          {labels.submit}
        </Button>
      </div>
    </form>
  );
}
