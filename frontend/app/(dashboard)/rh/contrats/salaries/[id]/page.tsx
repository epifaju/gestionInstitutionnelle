"use client";

import type { ColumnDef } from "@tanstack/react-table";
import Link from "next/link";
import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { CreerContratModal } from "@/app/(dashboard)/rh/contrats/CreerContratModal";
import { DecisionFinModal } from "@/app/(dashboard)/rh/contrats/DecisionFinModal";
import { FileUploadModal } from "@/app/(dashboard)/rh/contrats/FileUploadModal";
import { FormationModal } from "@/app/(dashboard)/rh/contrats/FormationModal";
import { PlanifierVisiteModal } from "@/app/(dashboard)/rh/contrats/PlanifierVisiteModal";
import { RenouvellementModal } from "@/app/(dashboard)/rh/contrats/RenouvellementModal";
import { RenouvelerFormationModal } from "@/app/(dashboard)/rh/contrats/RenouvelerFormationModal";
import { ResultatVisiteModal } from "@/app/(dashboard)/rh/contrats/ResultatVisiteModal";
import { StatutRenouvellementModal } from "@/app/(dashboard)/rh/contrats/StatutRenouvellementModal";
import { TitreSejourModal } from "@/app/(dashboard)/rh/contrats/TitreSejourModal";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DataTable } from "@/components/tables/DataTable";
import { GenerateDocumentDialog } from "@/components/templates/GenerateDocumentDialog";
import { useAuthStore } from "@/lib/store";
import { cn } from "@/lib/utils";
import { getSalarie, listDocuments } from "@/services/salarie.service";
import {
  getContratActif,
  getFormations,
  getHistoriqueContrats,
  getTitresSejour,
  getVisitesSalarie,
  uploadContratSigne,
} from "@/services/contrat.service";
import type { ContratResponse, FormationObligatoireResponse, TitreSejourResponse } from "@/types/contrat.types";

function statutVariant(s: string): "success" | "warning" | "muted" | "default" {
  if (s === "ACTIF") return "success";
  if (s === "EN_CONGE") return "warning";
  if (s === "SORTI") return "muted";
  return "default";
}

function joursContratBadge(j: number | null | undefined) {
  if (j == null) return { className: "bg-muted text-foreground", label: "—" };
  if (j < 0) return { className: "bg-red-950 text-white", label: "EXPIRÉ" };
  if (j === 0) return { className: "bg-red-800 text-white animate-pulse", label: "0j" };
  if (j <= 7) return { className: "bg-red-500 text-white animate-pulse", label: `${j}j` };
  if (j <= 30) return { className: "bg-orange-500 text-white", label: `${j}j` };
  return { className: "bg-emerald-600 text-white", label: `${j}j` };
}

export default function RhContratsSalariePage() {
  const t = useTranslations("RH.contrats.salariePage");
  const ts = useTranslations("RH.salaries");
  const tc = useTranslations("Common");
  const params = useParams();
  const id = String(params.id);
  const qc = useQueryClient();
  const isRh = useAuthStore((s) => s.user?.role === "RH");
  const isAdmin = useAuthStore((s) => s.user?.role === "ADMIN");

  const { data: salarie, isLoading: salLoading } = useQuery({
    queryKey: ["rh", "salarie", id],
    queryFn: () => getSalarie(id),
  });

  const { data: actif } = useQuery({
    queryKey: ["rh", "contrats", "actif", id],
    queryFn: () => getContratActif(id),
    retry: false,
  });

  const isRhOrAdmin = isRh || isAdmin;
  const { data: dossierDocs } = useQuery({
    queryKey: ["rh", "docs", id],
    queryFn: () => listDocuments(id),
    enabled: isRhOrAdmin,
  });

  const { data: hist } = useQuery({
    queryKey: ["rh", "contrats", "hist", id],
    queryFn: () => getHistoriqueContrats(id),
  });

  const { data: visites } = useQuery({
    queryKey: ["rh", "contrats", "visites", id],
    queryFn: () => getVisitesSalarie(id),
  });

  const { data: titres } = useQuery({
    queryKey: ["rh", "contrats", "titres", id],
    queryFn: () => getTitresSejour(id),
  });

  const { data: formations } = useQuery({
    queryKey: ["rh", "contrats", "formations", id],
    queryFn: () => getFormations(id),
  });

  const [creerOpen, setCreerOpen] = useState(false);
  const [renOpen, setRenOpen] = useState(false);
  const [decOpen, setDecOpen] = useState(false);
  const [upOpen, setUpOpen] = useState(false);
  const [selContrat, setSelContrat] = useState<ContratResponse | null>(null);

  const [visiteOpen, setVisiteOpen] = useState(false);
  const [resOpen, setResOpen] = useState(false);
  const [selVisite, setSelVisite] = useState<string | null>(null);

  const [titreOpen, setTitreOpen] = useState(false);
  const [stTitreOpen, setStTitreOpen] = useState(false);
  const [selTitre, setSelTitre] = useState<TitreSejourResponse | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [renFormOpen, setRenFormOpen] = useState(false);
  const [formationMode, setFormationMode] = useState<"renew" | "cert">("renew");
  const [selFormation, setSelFormation] = useState<FormationObligatoireResponse | null>(null);

  const derniereVisite = useMemo(() => (visites && visites.length ? visites[0] : null), [visites]);

  const colonnesTitres: ColumnDef<TitreSejourResponse>[] = useMemo(
    () => [
      { accessorKey: "typeDocument", header: t("titres.col.type") },
      { accessorKey: "numeroDocument", header: t("titres.col.numero") },
      { accessorKey: "paysEmetteur", header: t("titres.col.pays") },
      { accessorKey: "dateExpiration", header: t("titres.col.exp") },
      { accessorKey: "statutRenouvellement", header: t("titres.col.statut") },
      {
        id: "actions",
        header: t("titres.col.actions"),
        cell: ({ row }) => {
          const x = row.original;
          return (
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="outline"
                type="button"
                onClick={() => {
                  setSelTitre(x);
                  setTitreOpen(true);
                }}
              >
                {t("titres.actionUpload")}
              </Button>
              <Button
                size="sm"
                type="button"
                onClick={() => {
                  setSelTitre(x);
                  setStTitreOpen(true);
                }}
              >
                {t("titres.actionStatut")}
              </Button>
            </div>
          );
        },
      },
    ],
    [t]
  );

  const colonnesFormations: ColumnDef<FormationObligatoireResponse>[] = useMemo(
    () => [
      { accessorKey: "intitule", header: t("formations.col.intitule") },
      { accessorKey: "typeFormation", header: t("formations.col.type") },
      { accessorKey: "organisme", header: t("formations.col.org") },
      { accessorKey: "dateRealisation", header: t("formations.col.real") },
      { accessorKey: "dateExpiration", header: t("formations.col.exp") },
      { accessorKey: "statut", header: t("formations.col.statut") },
      {
        id: "actions",
        header: t("formations.col.actions"),
        cell: ({ row }) => {
          const f = row.original;
          return (
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="outline"
                type="button"
                onClick={() => {
                  setFormationMode("cert");
                  setSelFormation(f);
                  setRenFormOpen(true);
                }}
              >
                {t("formations.actionCert")}
              </Button>
              <Button
                size="sm"
                type="button"
                onClick={() => {
                  setFormationMode("renew");
                  setSelFormation(f);
                  setRenFormOpen(true);
                }}
              >
                {t("formations.actionRenouv")}
              </Button>
            </div>
          );
        },
      },
    ],
    [t]
  );

  if (salLoading || !salarie) {
    return <p className="p-4 text-muted-foreground">{tc("loading")}</p>;
  }

  const contratCard = actif ?? null;
  const j = contratCard?.joursAvantFin;
  const jb = joursContratBadge(j);

  return (
    <div className="space-y-6">
      <div className="text-sm text-muted-foreground">
        <Link href="/rh/salaries" className="text-indigo-600 hover:underline">
          {ts("title")}
        </Link>{" "}
        <span className="text-muted-foreground">/</span>{" "}
        <span className="text-foreground">
          {salarie.prenom} {salarie.nom}
        </span>{" "}
        <span className="text-muted-foreground">/</span> <span className="text-foreground">{t("breadcrumbLeaf")}</span>
      </div>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">
            {t("title", { prenom: salarie.prenom, nom: salarie.nom })}
          </h1>
          <p className="text-sm text-muted-foreground">
            {salarie.matricule} · {salarie.service}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={statutVariant(salarie.statut)}>{salarie.statut}</Badge>
          <Link href={`/rh/salaries/${id}`} className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
            {t("backSalarie")}
          </Link>
        </div>
      </div>

      <Card className="p-4">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold text-foreground">{t("contratActif")}</h2>
          {!contratCard && isRhOrAdmin ? (
            <Button type="button" size="sm" onClick={() => setCreerOpen(true)}>
              {t("creerContratBtn")}
            </Button>
          ) : null}
          {contratCard && (isRh || isAdmin) ? (
            <div className="flex flex-wrap gap-2">
              <GenerateDocumentDialog subjectType="CONTRAT" subjectId={contratCard.id} />
              {isRh && contratCard.typeContrat === "CDD" && contratCard.decisionFin === "EN_ATTENTE" ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setSelContrat(contratCard);
                    setRenOpen(true);
                  }}
                >
                  {t("actions.renouveler")}
                </Button>
              ) : null}
              {isRh ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setSelContrat(contratCard);
                    setDecOpen(true);
                  }}
                >
                  {t("actions.decision")}
                </Button>
              ) : null}
              {isRh || isAdmin ? (
                <Button
                  type="button"
                  size="sm"
                  onClick={() => {
                    setSelContrat(contratCard);
                    setUpOpen(true);
                  }}
                >
                  {t("actions.upload")}
                </Button>
              ) : null}
            </div>
          ) : null}
        </div>

        {!contratCard ? (
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">{t("noContrat")}</p>
            {isRhOrAdmin ? (
              <>
                <p className="text-sm text-muted-foreground">{t("noContratExplain")}</p>
                {(dossierDocs ?? []).length > 0 ? (
                  <div>
                    <p className="mb-2 text-sm font-medium text-foreground">{t("dossierPdfTitle")}</p>
                    <ul className="space-y-1 rounded-md border border-border bg-muted/30 p-3 text-sm">
                      {(dossierDocs ?? []).map((d) => (
                        <li key={d.url} className="flex flex-wrap items-center justify-between gap-2">
                          <span className="text-foreground">{d.nomFichier}</span>
                          <a className="text-indigo-600 hover:underline" href={d.url} target="_blank" rel="noreferrer">
                            {tc("open")}
                          </a>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </>
            ) : null}
          </div>
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            <div className="text-sm">
              <div>
                <span className="text-muted-foreground">{t("fields.type")}</span> {contratCard.typeContrat}
              </div>
              <div>
                <span className="text-muted-foreground">{t("fields.debut")}</span> {contratCard.dateDebutContrat}
              </div>
              <div>
                <span className="text-muted-foreground">{t("fields.fin")}</span> {contratCard.dateFinContrat ?? tc("emDash")}
              </div>
              <div>
                <span className="text-muted-foreground">{t("fields.pe")}</span> {contratCard.dateFinPeriodeEssai ?? tc("emDash")}
              </div>
            </div>
            <div className="text-sm">
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground">{t("fields.jours")}</span>
                <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${jb.className}`}>{jb.label}</span>
              </div>
              <div>
                <span className="text-muted-foreground">{t("fields.decision")}</span> {contratCard.decisionFin ?? tc("emDash")}
              </div>
              {contratCard.contratSigneUrl ? (
                <div className="mt-2">
                  <a
                    href={contratCard.contratSigneUrl}
                    target="_blank"
                    rel="noreferrer"
                    className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}
                  >
                    {t("actions.voirContrat")}
                  </a>
                </div>
              ) : null}
            </div>
          </div>
        )}
      </Card>

      <Card className="p-4">
        <h2 className="mb-3 font-semibold text-foreground">{t("histTitle")}</h2>
        {(hist ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("histEmpty")}</p>
        ) : (
          <div className="relative space-y-4 border-l-2 border-indigo-200 pl-4">
            {(hist ?? []).map((c) => (
              <div key={c.id} className="relative">
                <span className="absolute -left-[21px] top-1 h-3 w-3 rounded-full bg-indigo-500" />
                <p className="text-sm font-medium text-foreground">
                  {c.typeContrat} · {c.dateDebutContrat} → {c.dateFinContrat ?? "—"}
                </p>
                <p className="text-xs text-muted-foreground">
                  {t("histMeta", { decision: c.decisionFin ?? "—", n: c.renouvellementNumero ?? 0 })}
                </p>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card className="p-4">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold text-foreground">{t("medicalTitle")}</h2>
          <Button type="button" size="sm" onClick={() => setVisiteOpen(true)}>
            {t("medicalPlan")}
          </Button>
        </div>
        {!derniereVisite ? (
          <p className="text-sm text-muted-foreground">{t("medicalEmpty")}</p>
        ) : (
          <div className="grid gap-3 md:grid-cols-2 text-sm">
            <div>
              <div>
                <span className="text-muted-foreground">{t("medical.lastReal")}</span> {derniereVisite.dateRealisee ?? tc("emDash")}
              </div>
              <div>
                <span className="text-muted-foreground">{t("medical.result")}</span> {derniereVisite.resultat ?? tc("emDash")}
              </div>
            </div>
            <div>
              <div>
                <span className="text-muted-foreground">{t("medical.next")}</span> {derniereVisite.prochaineVisite ?? tc("emDash")}
              </div>
            </div>
          </div>
        )}

        <details className="mt-4 rounded-md border border-border p-3">
          <summary className="cursor-pointer text-sm font-medium text-foreground">{t("medicalPast")}</summary>
          <div className="mt-3 space-y-2 text-sm">
            {(visites ?? []).map((v) => (
              <div key={v.id} className="flex flex-wrap items-center justify-between gap-2 border-b border-border py-2 last:border-b-0">
                <div>
                  <div className="font-medium text-foreground">{v.typeVisite}</div>
                  <div className="text-xs text-muted-foreground">
                    {v.datePlanifiee ?? "—"} → {v.dateRealisee ?? "—"} · {v.resultat ?? "—"}
                  </div>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  type="button"
                  onClick={() => {
                    setSelVisite(v.id);
                    setResOpen(true);
                  }}
                >
                  {t("medicalResultBtn")}
                </Button>
              </div>
            ))}
          </div>
        </details>
      </Card>

      <Card className="p-4">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold text-foreground">{t("titresTitle")}</h2>
          <Button
            type="button"
            size="sm"
            onClick={() => {
              setSelTitre(null);
              setTitreOpen(true);
            }}
          >
            {t("titres.add")}
          </Button>
        </div>
        <DataTable columns={colonnesTitres} data={titres ?? []} />
      </Card>

      <Card className="p-4">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold text-foreground">{t("formationsTitle")}</h2>
          <Button type="button" size="sm" onClick={() => setFormOpen(true)}>
            {t("formations.add")}
          </Button>
        </div>
        <DataTable columns={colonnesFormations} data={formations ?? []} />
      </Card>

      <CreerContratModal
        open={creerOpen}
        onOpenChange={setCreerOpen}
        salarieId={id}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "actif", id] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "hist", id] });
        }}
      />
      <RenouvellementModal
        open={renOpen}
        onOpenChange={setRenOpen}
        contrat={selContrat}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "actif", id] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "hist", id] });
        }}
      />
      <DecisionFinModal
        open={decOpen}
        onOpenChange={setDecOpen}
        contrat={selContrat}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "actif", id] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "hist", id] });
        }}
      />
      <FileUploadModal
        open={upOpen}
        onOpenChange={setUpOpen}
        title={t("uploadTitle")}
        accept="application/pdf"
        onUpload={async (file) => {
          if (!selContrat) return;
          await uploadContratSigne(selContrat.id, file);
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "actif", id] });
        }}
      />

      <PlanifierVisiteModal
        open={visiteOpen}
        onOpenChange={setVisiteOpen}
        defaultSalarieId={id}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "visites", id] });
        }}
      />
      <ResultatVisiteModal
        open={resOpen}
        onOpenChange={setResOpen}
        visiteId={selVisite}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "visites", id] });
        }}
      />

      <TitreSejourModal
        open={titreOpen}
        onOpenChange={(v) => {
          setTitreOpen(v);
          if (!v) setSelTitre(null);
        }}
        defaultSalarieId={id}
        preset={
          selTitre
            ? {
                typeDocument: selTitre.typeDocument,
                numeroDocument: selTitre.numeroDocument,
                paysEmetteur: selTitre.paysEmetteur,
                dateEmission: selTitre.dateEmission,
                dateExpiration: selTitre.dateExpiration,
                autoriteEmettrice: selTitre.autoriteEmettrice,
              }
            : null
        }
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "titres", id] });
        }}
      />
      <StatutRenouvellementModal
        open={stTitreOpen}
        onOpenChange={(v) => {
          setStTitreOpen(v);
          if (!v) setSelTitre(null);
        }}
        titre={selTitre}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "titres", id] });
        }}
      />

      <FormationModal
        open={formOpen}
        onOpenChange={setFormOpen}
        defaultSalarieId={id}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "formations", id] });
        }}
      />
      <RenouvelerFormationModal
        open={renFormOpen}
        onOpenChange={(v) => {
          setRenFormOpen(v);
          if (!v) setSelFormation(null);
        }}
        formation={selFormation}
        mode={formationMode}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "formations", id] });
        }}
      />
    </div>
  );
}
