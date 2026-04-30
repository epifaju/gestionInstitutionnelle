"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { getJob, type ExportJobResponse } from "@/services/export-conformite.service";

export function useExportJob(jobId: string | null) {
  const query = useQuery({
    queryKey: ["exports", "job", jobId],
    queryFn: () => (jobId ? getJob(jobId) : Promise.resolve(null)),
    enabled: !!jobId,
    refetchInterval: (q) => {
      const job = q.state.data as ExportJobResponse | null | undefined;
      if (!job) return false;
      if (job.statut === "EN_ATTENTE" || job.statut === "EN_COURS") return 2000;
      return false;
    },
  });

  const job = (query.data ?? null) as ExportJobResponse | null;

  const isTermine = job?.statut === "TERMINE";
  const isErreur = job?.statut === "ERREUR";

  const telecharger = () => {
    if (!job?.fichierUrl) return;
    window.open(job.fichierUrl, "_blank", "noopener,noreferrer");
  };

  return useMemo(
    () => ({
      job,
      isLoading: query.isLoading,
      isTermine,
      isErreur,
      telecharger,
      refetch: query.refetch,
    }),
    [job, query.isLoading, isTermine, isErreur, query.refetch]
  );
}

