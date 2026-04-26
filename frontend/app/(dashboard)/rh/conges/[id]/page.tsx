"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { getCongeById } from "@/services/conge.service";
import { useAuthStore } from "@/lib/store";

export default function CongeDetailPage() {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.conges");
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const user = useAuthStore((s) => s.user);
  const allowed = !!user;

  const id = params?.id;
  const q = useQuery({
    queryKey: ["conge", id],
    queryFn: () => getCongeById(id),
    enabled: allowed && !!id,
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <Button type="button" variant="outline" onClick={() => router.push("/rh/conges")}>
          {tc("back")}
        </Button>
      </div>

      <div className="rounded-lg border border-border bg-card p-4 text-card-foreground">
        {q.isLoading ? (
          <p className="text-sm text-muted-foreground">{tc("loading")}</p>
        ) : q.data ? (
          <div className="grid gap-3 md:grid-cols-2">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("thSalarie")}</p>
              <p className="text-sm font-medium text-foreground">{q.data.salarieNomComplet}</p>
              <p className="text-xs text-muted-foreground">{q.data.service ?? tc("emDash")}</p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("thStatut")}</p>
              <p className="text-sm font-medium text-foreground">{q.data.statut}</p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("thType")}</p>
              <p className="text-sm text-foreground">{q.data.typeConge}</p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("thJours")}</p>
              <p className="text-sm text-foreground">{q.data.nbJours}</p>
            </div>
            <div className="md:col-span-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("thPeriode")}</p>
              <p className="text-sm text-foreground">
                {q.data.dateDebut} → {q.data.dateFin}
              </p>
            </div>
            {q.data.motifRejet ? (
              <div className="md:col-span-2">
                <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("rejectModalTitle")}</p>
                <p className="text-sm text-foreground">{q.data.motifRejet}</p>
              </div>
            ) : null}
            {q.data.commentaire ? (
              <div className="md:col-span-2">
                <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Commentaire</p>
                <p className="text-sm text-foreground whitespace-pre-wrap">{q.data.commentaire}</p>
              </div>
            ) : null}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">{tc("errorGeneric")}</p>
        )}
      </div>
    </div>
  );
}

