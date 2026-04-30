"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Loader2, Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { ExportJobResponse } from "@/services/export-conformite.service";
import { ExportProgressCard } from "@/components/exports/ExportProgressCard";
import axios from "axios";

type Variant = "pdf" | "excel" | "csv";

function variantClass(v: Variant) {
  if (v === "excel") return "bg-emerald-600 text-white hover:bg-emerald-700";
  if (v === "csv") return "bg-slate-700 text-white hover:bg-slate-800";
  return "bg-indigo-600 text-white hover:bg-indigo-700";
}

export function ExportButton({
  label,
  icon,
  onExport,
  variant = "pdf",
  disabled,
}: {
  label: string;
  icon?: React.ReactNode;
  onExport: () => Promise<ExportJobResponse>;
  variant?: Variant;
  disabled?: boolean;
}) {
  const [loading, setLoading] = useState(false);
  const [job, setJob] = useState<ExportJobResponse | null>(null);
  const [readyUrl, setReadyUrl] = useState<string | null>(null);
  const [readyName, setReadyName] = useState<string | null>(null);

  async function run() {
    try {
      setLoading(true);
      setReadyUrl(null);
      setReadyName(null);
      const res = await onExport();
      setJob(res);
      if (res.statut === "TERMINE" && res.fichierUrl) {
        setReadyUrl(res.fichierUrl);
        setReadyName(res.nomFichier ?? null);
      }
      if (res.statut === "ERREUR") {
        toast.error(res.messageErreur ?? "Export en erreur.");
      }
    } catch (e) {
      // Les erreurs HTTP (400/403/500...) sont déjà toastées par l'intercepteur global.
      // Ici on ne garde que les erreurs "réseau" sans réponse.
      if (axios.isAxiosError(e) && e.response) return;
      toast.error("Impossible de lancer l’export.");
    } finally {
      setLoading(false);
    }
  }

  const canDownload = !!readyUrl;

  return (
    <div>
      <div className="flex items-center gap-2">
        <Button
          type="button"
          className={variantClass(variant)}
          onClick={canDownload ? () => window.open(readyUrl!, "_blank", "noopener,noreferrer") : run}
          disabled={disabled || loading}
        >
          {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : canDownload ? <Download className="mr-2 h-4 w-4" /> : icon}
          {loading ? "Préparation..." : canDownload ? `Télécharger${readyName ? ` (${readyName})` : ""}` : label}
        </Button>
      </div>

      {job && job.id && (job.statut === "EN_ATTENTE" || job.statut === "EN_COURS" || job.statut === "TERMINE" || job.statut === "ERREUR") ? (
        <ExportProgressCard job={job} onRetry={run} />
      ) : null}
    </div>
  );
}

