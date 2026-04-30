"use client";

import { useMemo } from "react";
import { FileText, FileSpreadsheet, ListChecks, RefreshCw, Download } from "lucide-react";
import type { ExportJobResponse, TypeExport } from "@/services/export-conformite.service";
import { useExportJob } from "@/hooks/useExportJob";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";

function iconFor(type: TypeExport) {
  if (type.endsWith("_EXCEL")) return <FileSpreadsheet className="h-4 w-4" />;
  if (type.endsWith("_CSV")) return <ListChecks className="h-4 w-4" />;
  return <FileText className="h-4 w-4" />;
}

function formatBytes(n: number | null) {
  if (!n || n <= 0) return null;
  const kb = n / 1024;
  if (kb < 1024) return `${kb.toFixed(0)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(1)} MB`;
}

function hoursLeft(expireA: string | null) {
  if (!expireA) return null;
  const d = new Date(expireA);
  if (Number.isNaN(d.getTime())) return null;
  const ms = d.getTime() - Date.now();
  const h = Math.max(0, Math.floor(ms / 3600_000));
  return h;
}

export function ExportProgressCard({
  job,
  onRetry,
}: {
  job: ExportJobResponse;
  onRetry?: () => void;
}) {
  const { job: live, telecharger, isErreur, isTermine, isLoading, refetch } = useExportJob(job.id);
  const j = live ?? job;
  const ttl = useMemo(() => hoursLeft(j.expireA ?? null), [j.expireA]);

  return (
    <Card className="mt-3 p-4">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-2">
          <div className="rounded-md bg-muted p-2 text-muted-foreground">{iconFor(j.typeExport)}</div>
          <div>
            <p className="text-sm font-medium">
              {j.statut === "TERMINE" ? "Export prêt" : j.statut === "ERREUR" ? "Erreur d’export" : "Génération en cours..."}
            </p>
            <p className="text-xs text-muted-foreground">
              {j.nomFichier ? j.nomFichier : j.typeExport}{" "}
              {j.tailleOctets ? <span className="ml-2">({formatBytes(j.tailleOctets)})</span> : null}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {j.statut === "TERMINE" && j.fichierUrl ? (
            <Button type="button" variant="secondary" className="bg-emerald-600 text-white hover:bg-emerald-700" onClick={telecharger}>
              <Download className="mr-2 h-4 w-4" />
              Télécharger
            </Button>
          ) : null}

          {j.statut === "ERREUR" ? (
            <Button type="button" variant="secondary" onClick={onRetry}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Réessayer
            </Button>
          ) : null}

          {(j.statut === "EN_ATTENTE" || j.statut === "EN_COURS") && !isLoading ? (
            <Button type="button" variant="ghost" onClick={() => refetch()}>
              Actualiser
            </Button>
          ) : null}
        </div>
      </div>

      {j.statut === "EN_ATTENTE" || j.statut === "EN_COURS" ? (
        <div className="mt-3 space-y-2">
          <Progress value={j.progression} />
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>{j.progression}%</span>
            {ttl != null ? <span>Expire dans {ttl}h</span> : <span>Disponible 48h</span>}
          </div>
        </div>
      ) : null}

      {isTermine ? (
        <p className="mt-2 text-xs text-muted-foreground">Disponible 48h{ttl != null ? ` — expire dans ${ttl}h` : ""}.</p>
      ) : null}

      {isErreur ? (
        <p className="mt-2 text-sm text-red-600">{j.messageErreur ?? "Une erreur est survenue pendant la génération."}</p>
      ) : null}
    </Card>
  );
}

