"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { FileSpreadsheet, FileText, ListChecks, Download } from "lucide-react";
import { getMesJobs, type ExportJobResponse, type TypeExport } from "@/services/export-conformite.service";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";

function iconFor(type: TypeExport) {
  if (type.endsWith("_EXCEL")) return <FileSpreadsheet className="h-4 w-4" />;
  if (type.endsWith("_CSV")) return <ListChecks className="h-4 w-4" />;
  return <FileText className="h-4 w-4" />;
}

function badgeVariant(statut: ExportJobResponse["statut"]) {
  if (statut === "TERMINE") return "default";
  if (statut === "ERREUR") return "destructive";
  if (statut === "EXPIRE") return "secondary";
  return "outline";
}

export function MesExportsRecents() {
  const { data, isLoading } = useQuery({
    queryKey: ["exports", "mes-jobs", 0],
    queryFn: () => getMesJobs(0),
    staleTime: 10_000,
  });

  const jobs = useMemo(() => (data?.content ?? []).slice(0, 5), [data]);

  return (
    <Card className="p-4">
      <div className="mb-3 flex items-center justify-between">
        <p className="text-sm font-semibold">Mes exports récents</p>
        <Badge variant="secondary">{jobs.length}</Badge>
      </div>

      {isLoading ? <p className="text-sm text-muted-foreground">Chargement...</p> : null}

      <div className="space-y-2">
        {jobs.map((j) => (
          <div key={j.id ?? j.createdAt ?? Math.random()} className="flex items-center justify-between gap-3 rounded-md border p-2">
            <div className="flex min-w-0 items-center gap-2">
              <div className="text-muted-foreground">{iconFor(j.typeExport)}</div>
              <div className="min-w-0">
                <p className="truncate text-sm">{j.nomFichier ?? j.typeExport}</p>
                <p className="text-xs text-muted-foreground">{j.createdAt ? new Date(j.createdAt).toLocaleString() : ""}</p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Badge variant={badgeVariant(j.statut)}>{j.statut === "EN_ATTENTE" ? "En attente" : j.statut === "EN_COURS" ? "En cours" : j.statut === "TERMINE" ? "Terminé" : j.statut === "ERREUR" ? "Erreur" : "Expiré"}</Badge>
              {j.statut === "TERMINE" && j.fichierUrl ? (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() => window.open(j.fichierUrl!, "_blank", "noopener,noreferrer")}
                >
                  <Download className="h-4 w-4" />
                </Button>
              ) : null}
            </div>
          </div>
        ))}

        {jobs.length === 0 && !isLoading ? <p className="text-sm text-muted-foreground">Aucun export récent.</p> : null}
      </div>
    </Card>
  );
}

