"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuthStore } from "@/lib/store";
import type { FraisRequest } from "@/lib/types/missions";
import {
  ajouterFrais,
  approuverMission,
  getMission,
  rembourserFrais,
  soumettreMission,
  terminerMission,
  uploadOrdreMission,
  uploadRapportMission,
  validerFrais,
} from "@/services/missions.service";

function statutVariant(s: string): "muted" | "info" | "warning" | "success" | "dangerSolid" {
  if (s === "BROUILLON") return "muted";
  if (s === "SOUMISE") return "info";
  if (s === "APPROUVEE") return "warning";
  if (s === "EN_COURS") return "info";
  if (s === "TERMINEE") return "success";
  return "dangerSolid";
}

function fmt(v: string | number | null | undefined) {
  if (v == null) return "—";
  const n = typeof v === "string" ? parseFloat(v) : v;
  return Number.isNaN(n) ? String(v) : n.toFixed(2);
}

export default function MissionDetailPage() {
  const t = useTranslations("Missions");
  const tc = useTranslations("Common");
  const qc = useQueryClient();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === "ADMIN";
  const isRh = user?.role === "RH";
  const isFin = user?.role === "FINANCIER";

  const { data: mission, isLoading } = useQuery({ queryKey: ["missions", id], queryFn: () => getMission(id) });

  const canApprove = isAdmin || isRh;
  const canValidateFrais = isAdmin || isRh || isFin;
  const canRembourser = isAdmin || isFin;

  const mutSoumettre = useMutation({
    mutationFn: () => soumettreMission(id),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["missions", id] });
      qc.invalidateQueries({ queryKey: ["missions", "list"] });
    },
  });
  const mutApprouver = useMutation({
    mutationFn: (avance: number | null) => approuverMission(id, avance),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["missions", id] });
      qc.invalidateQueries({ queryKey: ["missions", "list"] });
    },
  });
  const mutTerminer = useMutation({
    mutationFn: () => terminerMission(id),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["missions", id] });
      qc.invalidateQueries({ queryKey: ["missions", "list"] });
    },
  });

  const [frais, setFrais] = useState<FraisRequest>({
    typeFrais: "TRANSPORT",
    description: "",
    dateFrais: "",
    montant: 0,
    devise: "EUR",
  });
  const [just, setJust] = useState<File | null>(null);

  const mutAddFrais = useMutation({
    mutationFn: () => ajouterFrais(id, frais, just),
    onSuccess: () => {
      toast.success(tc("successCreated"));
      setFrais((f) => ({ ...f, description: "", montant: 0 }));
      setJust(null);
      qc.invalidateQueries({ queryKey: ["missions", id] });
    },
  });

  const mutValiderFrais = useMutation({
    mutationFn: ({ fraisId }: { fraisId: string }) => validerFrais(id, fraisId),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["missions", id] });
    },
  });

  const mutRembourserFrais = useMutation({
    mutationFn: ({ fraisId }: { fraisId: string }) => rembourserFrais(id, fraisId),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["missions", id] });
    },
  });

  const mutUploadOrdre = useMutation({
    mutationFn: (file: File) => uploadOrdreMission(id, file),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["missions", id] });
    },
  });
  const mutUploadRapport = useMutation({
    mutationFn: (file: File) => uploadRapportMission(id, file),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["missions", id] });
    },
  });

  const total = mission ? Number(fmt(mission.totalFraisValides)) : 0;
  const solde = mission ? Number(fmt(mission.soldeARegler)) : 0;

  const fraisRows = mission?.frais ?? [];
  const soldeColor = solde <= 0 ? "text-emerald-700" : "text-rose-700";

  const actionBar = useMemo(() => {
    if (!mission) return null;
    return (
      <div className="flex flex-wrap gap-2">
        {mission.statut === "BROUILLON" ? (
          <Button type="button" variant="outline" disabled={mutSoumettre.isPending} onClick={() => mutSoumettre.mutate()}>
            {t("actionSoumettre")}
          </Button>
        ) : null}

        {mission.statut === "SOUMISE" && canApprove ? (
          <Button
            type="button"
            disabled={mutApprouver.isPending}
            onClick={() => {
              const v = window.prompt(t("promptAvanceVersee"), "0");
              const n = v == null ? null : Number(v);
              mutApprouver.mutate(Number.isFinite(n) ? n : null);
            }}
          >
            {t("actionApprouver")}
          </Button>
        ) : null}

        {(mission.statut === "APPROUVEE" || mission.statut === "EN_COURS") && (canApprove || canRembourser) ? (
          <Button type="button" variant="outline" disabled={mutTerminer.isPending} onClick={() => mutTerminer.mutate()}>
            {t("actionTerminer")}
          </Button>
        ) : null}
      </div>
    );
  }, [mission, canApprove, canRembourser, mutSoumettre, mutApprouver, mutTerminer, t]);

  if (isLoading || !mission) {
    return <div>{tc("loading")}</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">{mission.titre}</h1>
          <p className="text-sm text-slate-600">
            {mission.destination} • {mission.dateDepart} → {mission.dateRetour} • {t("nbJours", { n: mission.nbJours })}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={statutVariant(mission.statut)}>{mission.statut}</Badge>
        </div>
      </div>

      {actionBar}

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="p-4 space-y-2 lg:col-span-2">
          <p className="text-sm font-semibold text-slate-900">{t("details")}</p>
          <p className="text-sm text-slate-700 whitespace-pre-wrap">{mission.objectif ?? "—"}</p>
          <div className="flex flex-wrap gap-2 pt-2">
            {mission.ordreMissionUrl ? (
              <a className="text-sm text-indigo-700 hover:underline" href={mission.ordreMissionUrl} target="_blank" rel="noreferrer">
                {t("downloadOrdre")}
              </a>
            ) : null}
            <Label className="inline-flex items-center gap-2">
              <span className="text-xs text-slate-500">{t("uploadOrdre")}</span>
              <input
                type="file"
                accept="application/pdf"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) mutUploadOrdre.mutate(f);
                }}
              />
            </Label>
            <Label className="inline-flex items-center gap-2">
              <span className="text-xs text-slate-500">{t("uploadRapport")}</span>
              <input
                type="file"
                accept="application/pdf"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) mutUploadRapport.mutate(f);
                }}
              />
            </Label>
          </div>
        </Card>

        <Card className="p-4 space-y-1">
          <p className="text-sm font-semibold text-slate-900">{t("resume")}</p>
          <p className="text-sm text-slate-700">
            {t("avanceVersee")}: {fmt(mission.avanceVersee)} EUR
          </p>
          <p className="text-sm text-slate-700">
            {t("totalFraisValides")}: {fmt(mission.totalFraisValides)} EUR
          </p>
          <p className={`text-sm font-semibold ${soldeColor}`}>
            {t("soldeARegler")}: {fmt(mission.soldeARegler)} EUR
          </p>
        </Card>
      </div>

      <Card className="p-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-semibold text-slate-900">{t("fraisTitle")}</p>
          <p className="text-xs text-slate-500">
            {t("totalsLine", { total: fmt(total), solde: fmt(solde) })}
          </p>
        </div>

        <div className="grid gap-3 md:grid-cols-5 items-end">
          <div className="space-y-1">
            <Label>{t("fTypeFrais")}</Label>
            <select
              className="flex h-9 w-full rounded border border-slate-200 px-2 text-sm"
              value={frais.typeFrais}
              onChange={(e) => setFrais((f) => ({ ...f, typeFrais: e.target.value }))}
            >
              {["TRANSPORT", "HEBERGEMENT", "REPAS", "VISA", "AUTRE"].map((x) => (
                <option key={x} value={x}>
                  {x}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1 md:col-span-2">
            <Label>{t("fDescription")}</Label>
            <Input value={frais.description} onChange={(e) => setFrais((f) => ({ ...f, description: e.target.value }))} />
          </div>
          <div className="space-y-1">
            <Label>{t("fDateFrais")}</Label>
            <Input type="date" value={frais.dateFrais} onChange={(e) => setFrais((f) => ({ ...f, dateFrais: e.target.value }))} />
          </div>
          <div className="space-y-1">
            <Label>{t("fMontant")}</Label>
            <Input
              type="number"
              inputMode="decimal"
              value={frais.montant}
              onChange={(e) => setFrais((f) => ({ ...f, montant: Number(e.target.value) }))}
            />
          </div>
          <div className="space-y-1">
            <Label>{t("fDevise")}</Label>
            <Input value={frais.devise} onChange={(e) => setFrais((f) => ({ ...f, devise: e.target.value }))} />
          </div>
          <div className="space-y-1 md:col-span-2">
            <Label>{t("fJustificatif")}</Label>
            <Input type="file" onChange={(e) => setJust(e.target.files?.[0] ?? null)} />
          </div>
          <div className="md:col-span-3 flex justify-end">
            <Button type="button" disabled={mutAddFrais.isPending} onClick={() => mutAddFrais.mutate()}>
              + {t("addFrais")}
            </Button>
          </div>
        </div>

        <div className="rounded-lg border border-slate-200">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("thType")}</TableHead>
                <TableHead>{t("thDesc")}</TableHead>
                <TableHead>{t("thDate")}</TableHead>
                <TableHead>{t("thMontant")}</TableHead>
                <TableHead>{t("thStatut")}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {fraisRows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6}>{t("emptyFrais")}</TableCell>
                </TableRow>
              ) : (
                fraisRows.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell>{r.typeFrais}</TableCell>
                    <TableCell className="max-w-[28rem] truncate" title={r.description}>
                      {r.description}
                    </TableCell>
                    <TableCell>{r.dateFrais}</TableCell>
                    <TableCell>
                      {fmt(r.montant)} {r.devise} ({fmt(r.montantEur)} EUR)
                    </TableCell>
                    <TableCell>
                      <Badge variant={statutVariant(r.statut)}>{r.statut}</Badge>
                    </TableCell>
                    <TableCell className="text-right space-x-2">
                      {r.justificatifUrl ? (
                        <a className="text-sm text-indigo-700 hover:underline" href={r.justificatifUrl} target="_blank" rel="noreferrer">
                          {t("openJust")}
                        </a>
                      ) : null}
                      {r.statut === "SOUMIS" && canValidateFrais ? (
                        <Button type="button" size="sm" variant="outline" onClick={() => mutValiderFrais.mutate({ fraisId: r.id })}>
                          {t("valider")}
                        </Button>
                      ) : null}
                      {r.statut === "VALIDE" && canRembourser ? (
                        <Button type="button" size="sm" onClick={() => mutRembourserFrais.mutate({ fraisId: r.id })}>
                          {t("rembourser")}
                        </Button>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </Card>
    </div>
  );
}

