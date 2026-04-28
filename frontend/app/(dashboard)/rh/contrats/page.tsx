"use client";

import type { ColumnDef } from "@tanstack/react-table";
import Link from "next/link";
import { useMemo, useState } from "react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { AlertTriangle, ExternalLink } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DataTable, type ServerPagination } from "@/components/tables/DataTable";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DecisionFinModal } from "@/app/(dashboard)/rh/contrats/DecisionFinModal";
import { EcheanceModal } from "@/app/(dashboard)/rh/contrats/EcheanceModal";
import { FileUploadModal } from "@/app/(dashboard)/rh/contrats/FileUploadModal";
import { FormationModal } from "@/app/(dashboard)/rh/contrats/FormationModal";
import { PlanifierVisiteModal } from "@/app/(dashboard)/rh/contrats/PlanifierVisiteModal";
import { RenouvellementModal } from "@/app/(dashboard)/rh/contrats/RenouvellementModal";
import { RenouvelerFormationModal } from "@/app/(dashboard)/rh/contrats/RenouvelerFormationModal";
import { ResultatVisiteModal } from "@/app/(dashboard)/rh/contrats/ResultatVisiteModal";
import { StatutRenouvellementModal } from "@/app/(dashboard)/rh/contrats/StatutRenouvellementModal";
import { TitreSejourModal } from "@/app/(dashboard)/rh/contrats/TitreSejourModal";
import { TraiterEcheanceModal } from "@/app/(dashboard)/rh/contrats/TraiterEcheanceModal";
import { useAuthStore } from "@/lib/store";
import { cn } from "@/lib/utils";
import {
  annulerEcheance,
  getContrats,
  getEcheances,
  getEcheancesDashboard,
  getFormations,
  getTitresSejour,
  getVisitesSalarie,
  uploadContratSigne,
} from "@/services/contrat.service";
import { listSalaries } from "@/services/salarie.service";
import type {
  ContratResponse,
  EcheanceResponse,
  FormationObligatoireResponse,
  TitreSejourResponse,
  VisiteMedicaleResponse,
} from "@/types/contrat.types";

type TabKey = "contrats" | "echeances" | "visites" | "titres" | "formations";

function urgenceBadge(niveau: string | null | undefined) {
  const n = (niveau ?? "NORMAL").toUpperCase();
  if (n === "CRITIQUE") return "bg-red-900 text-white";
  if (n === "URGENT") return "bg-red-500 text-white";
  if (n === "ATTENTION") return "bg-orange-500 text-white";
  return "bg-muted text-foreground";
}

function joursContratBadge(j: number | null | undefined) {
  if (j == null) return { className: "bg-muted text-foreground", label: "—" };
  if (j < 0) return { className: "bg-red-950 text-white", label: "EXPIRÉ" };
  if (j === 0) return { className: "bg-red-800 text-white animate-pulse", label: "0j" };
  if (j <= 7) return { className: "bg-red-500 text-white animate-pulse", label: `${j}j` };
  if (j <= 30) return { className: "bg-orange-500 text-white", label: `${j}j` };
  return { className: "bg-emerald-600 text-white", label: `${j}j` };
}

function joursExpirationBadge(j: number | null | undefined) {
  if (j == null) return { className: "bg-muted text-foreground", label: "—" };
  if (j < 0) return { className: "bg-red-950 text-white", label: "EXPIRÉ" };
  if (j <= 30) return { className: "bg-red-600 text-white", label: `${j}j` };
  if (j <= 90) return { className: "bg-orange-500 text-white", label: `${j}j` };
  return { className: "bg-emerald-600 text-white", label: `${j}j` };
}

type VisiteRow = VisiteMedicaleResponse & { service?: string | null };

export default function RhContratsPage() {
  const t = useTranslations("RH.contrats");
  const qc = useQueryClient();
  const isRh = useAuthStore((s) => s.user?.role === "RH");
  const isAdmin = useAuthStore((s) => s.user?.role === "ADMIN");

  const [tab, setTab] = useState<TabKey>("contrats");

  const { data: dash } = useQuery({
    queryKey: ["rh", "contrats", "dashboard"],
    queryFn: () => getEcheancesDashboard(),
  });

  // Contrats
  const [cPage, setCPage] = useState(0);
  const cSize = 20;
  const [cType, setCType] = useState<string>("");
  const [cService, setCService] = useState<string>("");
  const [cExpire, setCExpire] = useState<"all" | "30" | "60" | "90">("all");

  const { data: contratsPage, isLoading: cLoading } = useQuery({
    queryKey: ["rh", "contrats", "list", cPage, cSize, cType, cService],
    queryFn: () =>
      getContrats({
        page: cPage,
        size: cSize,
        typeContrat: cType || undefined,
        service: cService || undefined,
        sort: ["dateFinContrat,asc", "createdAt,desc"],
      }),
    enabled: tab === "contrats",
  });

  const contratsRows = useMemo(() => {
    const rows = contratsPage?.content ?? [];
    if (cExpire === "all") return rows;
    const max = Number(cExpire);
    return rows.filter((r) => {
      const j = r.joursAvantFin;
      if (j == null) return false;
      return j >= 0 && j <= max;
    });
  }, [contratsPage, cExpire]);

  const serviceOptions = useMemo(() => {
    const set = new Set<string>();
    for (const r of contratsPage?.content ?? []) {
      if (r.service) set.add(r.service);
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [contratsPage]);

  const [renOpen, setRenOpen] = useState(false);
  const [decOpen, setDecOpen] = useState(false);
  const [upOpen, setUpOpen] = useState(false);
  const [selContrat, setSelContrat] = useState<ContratResponse | null>(null);

  const mutUpload = useMutation({
    mutationFn: async ({ id, file }: { id: string; file: File }) => uploadContratSigne(id, file),
    onSuccess: async () => {
      toast.success(t("uploadOk"));
      await qc.invalidateQueries({ queryKey: ["rh", "contrats", "list"] });
    },
  });

  // Échéances
  const [ePage, setEPage] = useState(0);
  const eSize = 20;
  const [eStatut, setEStatut] = useState<string>("");
  const [eType, setEType] = useState<string>("");
  const [eService, setEService] = useState<string>("");
  const [eMin, setEMin] = useState<string>("");
  const [eMax, setEMax] = useState<string>("");
  const [echeanceOpen, setEcheanceOpen] = useState(false);
  const [traiterOpen, setTraiterOpen] = useState(false);
  const [selEcheance, setSelEcheance] = useState<EcheanceResponse | null>(null);

  const { data: echeancesPage, isLoading: eLoading } = useQuery({
    queryKey: ["rh", "contrats", "echeances", ePage, eSize, eStatut, eType, eService, eMin, eMax],
    queryFn: () =>
      getEcheances({
        page: ePage,
        size: eSize,
        statut: eStatut || undefined,
        type: eType || undefined,
        dateMin: eMin || undefined,
        dateMax: eMax || undefined,
        sort: ["dateEcheance,asc", "createdAt,asc"],
      }),
    enabled: tab === "echeances",
  });

  const echeancesRows = useMemo(() => {
    const rows = echeancesPage?.content ?? [];
    const q = eService.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => (r.service ?? "").toLowerCase().includes(q));
  }, [echeancesPage, eService]);

  // Agrégats RH (visites / titres / formations) : requêtes par salarié (liste courte)
  const { data: salariesPick } = useQuery({
    queryKey: ["rh", "contrats", "salaries-pick", tab],
    queryFn: () => listSalaries({ page: 0, size: 80, statut: "ACTIF" }),
    enabled: tab === "visites" || tab === "titres" || tab === "formations",
  });

  const salarieIds = useMemo(() => (salariesPick?.content ?? []).map((s) => s.id), [salariesPick]);

  const visitesQueries = useQueries({
    queries: salarieIds.map((id) => ({
      queryKey: ["rh", "contrats", "visites", id],
      queryFn: () => getVisitesSalarie(id),
      enabled: tab === "visites" && salarieIds.length > 0,
    })),
  });

  const visitesRows: VisiteRow[] = useMemo(() => {
    if (tab !== "visites") return [];
    const salaries = salariesPick?.content ?? [];
    const byId = new Map(salaries.map((s) => [s.id, s]));
    const rows: VisiteRow[] = [];
    visitesQueries.forEach((q, idx) => {
      const sid = salarieIds[idx];
      const s = sid ? byId.get(sid) : undefined;
      for (const v of q.data ?? []) {
        rows.push({ ...v, service: s?.service ?? null });
      }
    });
    rows.sort((a, b) => String(b.datePlanifiee ?? "").localeCompare(String(a.datePlanifiee ?? "")));
    return rows;
  }, [tab, salariesPick, visitesQueries, salarieIds]);

  const titresQueries = useQueries({
    queries: salarieIds.map((id) => ({
      queryKey: ["rh", "contrats", "titres", id],
      queryFn: () => getTitresSejour(id),
      enabled: tab === "titres" && salarieIds.length > 0,
    })),
  });

  const titreRows: (TitreSejourResponse & { service?: string | null })[] = useMemo(() => {
    if (tab !== "titres") return [];
    const salaries = salariesPick?.content ?? [];
    const byId = new Map(salaries.map((s) => [s.id, s]));
    const rows: (TitreSejourResponse & { service?: string | null })[] = [];
    titresQueries.forEach((q, idx) => {
      const sid = salarieIds[idx];
      const s = sid ? byId.get(sid) : undefined;
      for (const x of q.data ?? []) rows.push({ ...x, service: s?.service ?? null });
    });
    rows.sort((a, b) => String(a.dateExpiration).localeCompare(String(b.dateExpiration)));
    return rows;
  }, [tab, salariesPick, titresQueries, salarieIds]);

  const formationsQueries = useQueries({
    queries: salarieIds.map((id) => ({
      queryKey: ["rh", "contrats", "formations", id],
      queryFn: () => getFormations(id),
      enabled: tab === "formations" && salarieIds.length > 0,
    })),
  });

  const formationRows: (FormationObligatoireResponse & { service?: string | null })[] = useMemo(() => {
    if (tab !== "formations") return [];
    const salaries = salariesPick?.content ?? [];
    const byId = new Map(salaries.map((s) => [s.id, s]));
    const rows: (FormationObligatoireResponse & { service?: string | null })[] = [];
    formationsQueries.forEach((q, idx) => {
      const sid = salarieIds[idx];
      const s = sid ? byId.get(sid) : undefined;
      for (const x of q.data ?? []) rows.push({ ...x, service: s?.service ?? null });
    });
    rows.sort((a, b) => String(a.dateExpiration).localeCompare(String(b.dateExpiration)));
    return rows;
  }, [tab, salariesPick, formationsQueries, salarieIds]);

  const [visiteOpen, setVisiteOpen] = useState(false);
  const [resVisiteOpen, setResVisiteOpen] = useState(false);
  const [selVisiteId, setSelVisiteId] = useState<string | null>(null);

  const [titreOpen, setTitreOpen] = useState(false);
  const [stTitreOpen, setStTitreOpen] = useState(false);
  const [selTitre, setSelTitre] = useState<TitreSejourResponse | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [renFormOpen, setRenFormOpen] = useState(false);
  const [selFormation, setSelFormation] = useState<FormationObligatoireResponse | null>(null);
  const [formationMode, setFormationMode] = useState<"renew" | "cert">("renew");

  const columnsContrats: ColumnDef<ContratResponse>[] = useMemo(
    () => [
      {
        id: "salarie",
        header: t("th.salarie"),
        enableSorting: false,
        cell: ({ row }) => (
          <Link className="text-indigo-600 hover:underline" href={`/rh/contrats/salaries/${row.original.salarieId}`}>
            {row.original.salarieNomComplet}
          </Link>
        ),
      },
      { accessorKey: "service", header: t("th.service"), enableSorting: false },
      { accessorKey: "typeContrat", header: t("th.type"), enableSorting: false },
      { accessorKey: "dateDebutContrat", header: t("th.debut"), enableSorting: false },
      { accessorKey: "dateFinContrat", header: t("th.fin"), enableSorting: false },
      {
        accessorKey: "renouvellementNumero",
        header: t("th.renouv"),
        enableSorting: false,
        cell: ({ row }) => <span className="tabular-nums">{row.original.renouvellementNumero ?? 0}</span>,
      },
      {
        id: "jours",
        header: t("th.jours"),
        enableSorting: false,
        cell: ({ row }) => {
          const b = joursContratBadge(row.original.joursAvantFin);
          return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${b.className}`}>{b.label}</span>;
        },
      },
      { accessorKey: "decisionFin", header: t("th.decision"), enableSorting: false },
      {
        id: "actions",
        header: t("th.actions"),
        enableSorting: false,
        cell: ({ row }) => {
          const c = row.original;
          const canRhRenew = isRh && c.typeContrat === "CDD" && c.decisionFin === "EN_ATTENTE";
          const canRhDecision = isRh;
          return (
            <div className="flex flex-wrap gap-2">
              {canRhRenew ? (
                <Button
                  size="sm"
                  variant="outline"
                  type="button"
                  onClick={() => {
                    setSelContrat(c);
                    setRenOpen(true);
                  }}
                >
                  {t("actions.renouveler")}
                </Button>
              ) : null}
              {canRhDecision ? (
                <Button
                  size="sm"
                  variant="outline"
                  type="button"
                  onClick={() => {
                    setSelContrat(c);
                    setDecOpen(true);
                  }}
                >
                  {t("actions.decision")}
                </Button>
              ) : null}
              {c.contratSigneUrl ? (
                <a
                  href={c.contratSigneUrl}
                  target="_blank"
                  rel="noreferrer"
                  className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "inline-flex items-center gap-1")}
                >
                  {t("actions.voir")} <ExternalLink className="h-3.5 w-3.5" />
                </a>
              ) : null}
              {isRh || isAdmin ? (
                <Button
                  size="sm"
                  type="button"
                  onClick={() => {
                    setSelContrat(c);
                    setUpOpen(true);
                  }}
                >
                  {t("actions.upload")}
                </Button>
              ) : null}
            </div>
          );
        },
      },
    ],
    [isAdmin, isRh, t]
  );

  const columnsEcheances: ColumnDef<EcheanceResponse>[] = useMemo(
    () => [
      {
        id: "salarie",
        header: t("th.salarie"),
        enableSorting: false,
        cell: ({ row }) => (
          <Link className="text-indigo-600 hover:underline" href={`/rh/contrats/salaries/${row.original.salarieId}`}>
            {row.original.salarieNomComplet}
          </Link>
        ),
      },
      { accessorKey: "service", header: t("th.service"), enableSorting: false },
      {
        accessorKey: "typeEcheance",
        header: t("th.type"),
        enableSorting: false,
        cell: ({ row }) => <Badge variant="secondary">{row.original.typeEcheance}</Badge>,
      },
      { accessorKey: "titre", header: t("th.titre"), enableSorting: false },
      { accessorKey: "dateEcheance", header: t("th.date"), enableSorting: false },
      { accessorKey: "statut", header: t("th.statut"), enableSorting: false },
      {
        id: "urg",
        header: t("th.urgence"),
        enableSorting: false,
        cell: ({ row }) => {
          const n = (row.original.niveauUrgence ?? "NORMAL").toUpperCase();
          return (
            <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${urgenceBadge(n)}`}>
              {n === "CRITIQUE" ? <AlertTriangle className="h-3.5 w-3.5" /> : null}
              {n}
            </span>
          );
        },
      },
      {
        id: "actions",
        header: t("th.actions"),
        enableSorting: false,
        cell: ({ row }) => {
          const e = row.original;
          const canTreat = e.statut !== "TRAITEE" && e.statut !== "ANNULEE";
          return (
            <div className="flex flex-wrap gap-2">
              {canTreat ? (
                <Button
                  size="sm"
                  variant="outline"
                  type="button"
                  onClick={() => {
                    setSelEcheance(e);
                    setTraiterOpen(true);
                  }}
                >
                  {t("actions.traiter")}
                </Button>
              ) : null}
              <Button
                size="sm"
                variant="ghost"
                type="button"
                onClick={async () => {
                  const motif = window.prompt(t("promptAnnulerMotif"));
                  if (!motif) return;
                  await annulerEcheance(e.id, motif);
                  toast.success(t("annulerOk"));
                  await qc.invalidateQueries({ queryKey: ["rh", "contrats", "echeances"] });
                  await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
                }}
              >
                {t("actions.annuler")}
              </Button>
            </div>
          );
        },
      },
    ],
    [qc, t]
  );

  const columnsVisites: ColumnDef<VisiteRow>[] = useMemo(
    () => [
      {
        id: "salarie",
        header: t("th.salarie"),
        enableSorting: false,
        cell: ({ row }) => (
          <Link className="text-indigo-600 hover:underline" href={`/rh/contrats/salaries/${row.original.salarieId}`}>
            {row.original.salarieNomComplet}
          </Link>
        ),
      },
      { accessorKey: "service", header: t("th.service"), enableSorting: false },
      { accessorKey: "typeVisite", header: t("visites.th.type"), enableSorting: false },
      { accessorKey: "datePlanifiee", header: t("visites.th.plan"), enableSorting: false },
      { accessorKey: "dateRealisee", header: t("visites.th.real"), enableSorting: false },
      {
        id: "res",
        header: t("visites.th.resultat"),
        enableSorting: false,
        cell: ({ row }) => {
          const r = row.original.resultat;
          const cls =
            r === "INAPTE"
              ? "bg-red-600 text-white"
              : r === "APTE_AMENAGEMENT"
                ? "bg-orange-500 text-white"
                : r === "APTE"
                  ? "bg-emerald-600 text-white"
                  : "bg-muted text-foreground";
          return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${cls}`}>{r ?? "—"}</span>;
        },
      },
      { accessorKey: "prochaineVisite", header: t("visites.th.next"), enableSorting: false },
      {
        id: "actions",
        header: t("th.actions"),
        enableSorting: false,
        cell: ({ row }) => (
          <Button
            size="sm"
            variant="outline"
            type="button"
            onClick={() => {
              setSelVisiteId(row.original.id);
              setResVisiteOpen(true);
            }}
          >
            {t("visites.actionResultat")}
          </Button>
        ),
      },
    ],
    [t]
  );

  const columnsTitres: ColumnDef<TitreSejourResponse & { service?: string | null }>[] = useMemo(
    () => [
      {
        id: "salarie",
        header: t("th.salarie"),
        enableSorting: false,
        cell: ({ row }) => (
          <Link className="text-indigo-600 hover:underline" href={`/rh/contrats/salaries/${row.original.salarieId}`}>
            {row.original.salarieNomComplet}
          </Link>
        ),
      },
      { accessorKey: "typeDocument", header: t("titres.th.typeDoc"), enableSorting: false },
      { accessorKey: "numeroDocument", header: t("titres.th.numero"), enableSorting: false },
      { accessorKey: "paysEmetteur", header: t("titres.th.pays"), enableSorting: false },
      { accessorKey: "dateExpiration", header: t("titres.th.exp"), enableSorting: false },
      {
        id: "j",
        header: t("th.jours"),
        enableSorting: false,
        cell: ({ row }) => {
          const b = joursExpirationBadge(row.original.joursAvantExpiration);
          return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${b.className}`}>{b.label}</span>;
        },
      },
      { accessorKey: "statutRenouvellement", header: t("titres.th.statut"), enableSorting: false },
      {
        id: "actions",
        header: t("th.actions"),
        enableSorting: false,
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

  const columnsFormations: ColumnDef<FormationObligatoireResponse & { service?: string | null }>[] = useMemo(
    () => [
      {
        id: "salarie",
        header: t("th.salarie"),
        enableSorting: false,
        cell: ({ row }) => (
          <Link className="text-indigo-600 hover:underline" href={`/rh/contrats/salaries/${row.original.salarieId}`}>
            {row.original.salarieNomComplet}
          </Link>
        ),
      },
      { accessorKey: "intitule", header: t("formations.th.intitule"), enableSorting: false },
      { accessorKey: "typeFormation", header: t("th.type"), enableSorting: false },
      { accessorKey: "organisme", header: t("formations.th.org"), enableSorting: false },
      { accessorKey: "dateRealisation", header: t("formations.th.real"), enableSorting: false },
      { accessorKey: "dateExpiration", header: t("formations.th.exp"), enableSorting: false },
      {
        id: "j",
        header: t("th.jours"),
        enableSorting: false,
        cell: ({ row }) => {
          const b = joursExpirationBadge(row.original.joursAvantExpiration);
          return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${b.className}`}>{b.label}</span>;
        },
      },
      { accessorKey: "statut", header: t("th.statut"), enableSorting: false },
      {
        id: "actions",
        header: t("th.actions"),
        enableSorting: false,
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

  const cPagination: ServerPagination | undefined = contratsPage
    ? {
        page: contratsPage.page,
        size: contratsPage.size,
        totalElements: contratsPage.totalElements,
        totalPages: contratsPage.totalPages,
      }
    : undefined;

  const ePagination: ServerPagination | undefined = echeancesPage
    ? {
        page: echeancesPage.page,
        size: echeancesPage.size,
        totalElements: echeancesPage.totalElements,
        totalPages: echeancesPage.totalPages,
      }
    : undefined;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t("pageTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("pageSubtitle")}</p>
      </div>

      <div className="grid gap-3 md:grid-cols-5">
        <Card className="p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("kpi.cdd30")}</p>
          <div className="mt-2 flex items-center gap-2">
            <div className="text-2xl font-semibold tabular-nums">{dash?.finCddProchaines30j ?? "—"}</div>
            {(dash?.finCddProchaines30j ?? 0) > 0 ? <Badge className="bg-red-600 text-white">{t("badge.alert")}</Badge> : null}
          </div>
        </Card>
        <Card className="p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("kpi.pe15")}</p>
          <div className="mt-2 flex items-center gap-2">
            <div className="text-2xl font-semibold tabular-nums">{dash?.periodeEssaiProchaines15j ?? "—"}</div>
            {(dash?.periodeEssaiProchaines15j ?? 0) > 0 ? <Badge className="bg-orange-500 text-white">{t("badge.watch")}</Badge> : null}
          </div>
        </Card>
        <Card className="p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("kpi.visites")}</p>
          <div className="mt-2 text-2xl font-semibold tabular-nums">{dash?.visitesAPrevoir ?? "—"}</div>
        </Card>
        <Card className="p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("kpi.titres")}</p>
          <div className="mt-2 flex items-center gap-2">
            <div className="text-2xl font-semibold tabular-nums">{dash?.titresExpirantBientot ?? "—"}</div>
            {(dash?.titresExpirantBientot ?? 0) > 0 ? <Badge className="bg-red-600 text-white">{t("badge.alert")}</Badge> : null}
          </div>
        </Card>
        <Card className="p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("kpi.formations")}</p>
          <div className="mt-2 text-2xl font-semibold tabular-nums">{dash?.formationsARenouveler ?? "—"}</div>
        </Card>
      </div>

      <Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
        <TabsList className="w-full justify-start overflow-x-auto">
          <TabsTrigger value="contrats">{t("tabs.contrats")}</TabsTrigger>
          <TabsTrigger value="echeances">{t("tabs.echeances")}</TabsTrigger>
          <TabsTrigger value="visites">{t("tabs.visites")}</TabsTrigger>
          <TabsTrigger value="titres">{t("tabs.titres")}</TabsTrigger>
          <TabsTrigger value="formations">{t("tabs.formations")}</TabsTrigger>
        </TabsList>

        <TabsContent value="contrats" className="space-y-3">
          <div className="flex flex-wrap items-end gap-3">
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">{t("filters.typeContrat")}</label>
              <select
                className="h-9 rounded-lg border border-input bg-transparent px-2 text-sm"
                value={cType}
                onChange={(e) => {
                  setCType(e.target.value);
                  setCPage(0);
                }}
              >
                <option value="">{t("filters.all")}</option>
                <option value="CDI">CDI</option>
                <option value="CDD">CDD</option>
                <option value="INTERIM">INTERIM</option>
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">{t("filters.service")}</label>
              <select
                className="h-9 min-w-[12rem] rounded-lg border border-input bg-transparent px-2 text-sm"
                value={cService}
                onChange={(e) => {
                  setCService(e.target.value);
                  setCPage(0);
                }}
              >
                <option value="">{t("filters.all")}</option>
                {serviceOptions.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">{t("filters.expire")}</label>
              <select
                className="h-9 rounded-lg border border-input bg-transparent px-2 text-sm"
                value={cExpire}
                onChange={(e) => setCExpire(e.target.value as typeof cExpire)}
              >
                <option value="all">{t("filters.all")}</option>
                <option value="30">30j</option>
                <option value="60">60j</option>
                <option value="90">90j</option>
              </select>
            </div>
          </div>

          <DataTable<ContratResponse>
            columns={columnsContrats}
            data={contratsRows}
            isLoading={cLoading}
            pagination={cPagination}
            onPageChange={(p) => setCPage(p)}
          />
        </TabsContent>

        <TabsContent value="echeances" className="space-y-3">
          <div className="flex flex-wrap items-end gap-3">
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">{t("filters.statut")}</label>
              <select
                className="h-9 rounded-lg border border-input bg-transparent px-2 text-sm"
                value={eStatut}
                onChange={(e) => {
                  setEStatut(e.target.value);
                  setEPage(0);
                }}
              >
                <option value="">{t("filters.all")}</option>
                <option value="A_VENIR">A_VENIR</option>
                <option value="EN_ALERTE">EN_ALERTE</option>
                <option value="ACTION_REQUISE">ACTION_REQUISE</option>
                <option value="TRAITEE">TRAITEE</option>
                <option value="EXPIREE">EXPIREE</option>
                <option value="ANNULEE">ANNULEE</option>
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">{t("filters.typeEcheance")}</label>
              <select
                className="h-9 min-w-[14rem] rounded-lg border border-input bg-transparent px-2 text-sm"
                value={eType}
                onChange={(e) => {
                  setEType(e.target.value);
                  setEPage(0);
                }}
              >
                <option value="">{t("filters.all")}</option>
                <option value="FIN_CDD">FIN_CDD</option>
                <option value="FIN_PERIODE_ESSAI">FIN_PERIODE_ESSAI</option>
                <option value="RENOUVELLEMENT_CDD">RENOUVELLEMENT_CDD</option>
                <option value="VISITE_MEDICALE">VISITE_MEDICALE</option>
                <option value="TITRE_SEJOUR">TITRE_SEJOUR</option>
                <option value="FORMATION_OBLIGATOIRE">FORMATION_OBLIGATOIRE</option>
                <option value="AVENANT_CONTRAT">AVENANT_CONTRAT</option>
                <option value="AUTRE">AUTRE</option>
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">{t("filters.service")}</label>
              <Input
                className="h-9 w-44"
                value={eService}
                onChange={(e) => {
                  setEService(e.target.value);
                  setEPage(0);
                }}
                placeholder={t("filters.servicePlaceholder")}
              />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">{t("filters.du")}</label>
              <Input type="date" className="h-9 w-44" value={eMin} onChange={(e) => setEMin(e.target.value)} />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">{t("filters.au")}</label>
              <Input type="date" className="h-9 w-44" value={eMax} onChange={(e) => setEMax(e.target.value)} />
            </div>
          </div>

          <DataTable<EcheanceResponse>
            columns={columnsEcheances}
            data={echeancesRows}
            isLoading={eLoading}
            pagination={ePagination}
            onPageChange={(p) => setEPage(p)}
            actions={
              <Button type="button" onClick={() => setEcheanceOpen(true)}>
                {t("echeances.new")}
              </Button>
            }
          />
        </TabsContent>

        <TabsContent value="visites" className="space-y-3">
          <DataTable<VisiteRow>
            columns={columnsVisites}
            data={visitesRows}
            isLoading={visitesQueries.some((q) => q.isLoading)}
            actions={
              <Button type="button" onClick={() => setVisiteOpen(true)}>
                {t("visites.planifier")}
              </Button>
            }
          />
        </TabsContent>

        <TabsContent value="titres" className="space-y-3">
          <DataTable
            columns={columnsTitres}
            data={titreRows}
            isLoading={titresQueries.some((q) => q.isLoading)}
            actions={
              <Button
                type="button"
                onClick={() => {
                  setSelTitre(null);
                  setTitreOpen(true);
                }}
              >
                {t("titres.new")}
              </Button>
            }
          />
        </TabsContent>

        <TabsContent value="formations" className="space-y-3">
          <DataTable
            columns={columnsFormations}
            data={formationRows}
            isLoading={formationsQueries.some((q) => q.isLoading)}
            actions={
              <Button type="button" onClick={() => setFormOpen(true)}>
                {t("formations.new")}
              </Button>
            }
          />
        </TabsContent>
      </Tabs>

      <RenouvellementModal
        open={renOpen}
        onOpenChange={setRenOpen}
        contrat={selContrat}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "list"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
        }}
      />
      <DecisionFinModal
        open={decOpen}
        onOpenChange={setDecOpen}
        contrat={selContrat}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "list"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
        }}
      />
      <FileUploadModal
        open={upOpen}
        onOpenChange={setUpOpen}
        title={t("uploadTitle")}
        description={t("uploadHint")}
        accept="application/pdf"
        onUpload={async (file) => {
          if (!selContrat) return;
          await mutUpload.mutateAsync({ id: selContrat.id, file });
        }}
      />

      <EcheanceModal
        open={echeanceOpen}
        onOpenChange={setEcheanceOpen}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "echeances"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
        }}
      />
      <TraiterEcheanceModal
        open={traiterOpen}
        onOpenChange={setTraiterOpen}
        echeance={selEcheance}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "echeances"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
        }}
      />

      <PlanifierVisiteModal
        open={visiteOpen}
        onOpenChange={setVisiteOpen}
        salaries={salariesPick?.content}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "visites"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
        }}
      />
      <ResultatVisiteModal
        open={resVisiteOpen}
        onOpenChange={setResVisiteOpen}
        visiteId={selVisiteId}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "visites"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
        }}
      />

      <TitreSejourModal
        open={titreOpen}
        onOpenChange={(v) => {
          setTitreOpen(v);
          if (!v) setSelTitre(null);
        }}
        salaries={salariesPick?.content}
        defaultSalarieId={selTitre?.salarieId}
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
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "titres"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
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
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "titres"] });
        }}
      />

      <FormationModal
        open={formOpen}
        onOpenChange={setFormOpen}
        salaries={salariesPick?.content}
        onDone={async () => {
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "formations"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
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
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "formations"] });
          await qc.invalidateQueries({ queryKey: ["rh", "contrats", "dashboard"] });
        }}
      />
    </div>
  );
}
