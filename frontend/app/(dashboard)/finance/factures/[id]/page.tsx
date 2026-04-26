"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { FactureModal } from "@/components/finance/FactureModal";
import { PaiementModal } from "@/components/finance/PaiementModal";
import type { FactureRequest, PaiementRequest } from "@/lib/types/finance";
import { useAuthStore } from "@/lib/store";
import {
  changerStatutFacture,
  enregistrerPaiement,
  getFacture,
  listCategories,
  uploadJustificatifFacture,
  updateFacture,
} from "@/services/finance.service";
import { useState } from "react";
import { useTranslations } from "next-intl";

function statutBadge(s: string): "muted" | "warning" | "success" | "dangerSolid" {
  if (s === "BROUILLON") return "muted";
  if (s === "A_PAYER") return "warning";
  if (s === "PAYE") return "success";
  return "dangerSolid";
}

function fmt(v: string | number) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  return Number.isNaN(n) ? String(v) : n.toFixed(2);
}

export default function FactureDetailPage() {
  const t = useTranslations("Finance.factures");
  const tc = useTranslations("Common");
  const params = useParams();
  const id = String(params.id);
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const isFin = user?.role === "FINANCIER";
  const isAdmin = user?.role === "ADMIN";
  const canEdit = isFin || isAdmin;
  const [editOpen, setEditOpen] = useState(false);
  const [payOpen, setPayOpen] = useState(false);

  const { data: detail, isLoading } = useQuery({
    queryKey: ["finance", "facture", id],
    queryFn: () => getFacture(id),
  });

  const { data: categories } = useQuery({ queryKey: ["finance", "categories"], queryFn: listCategories });

  const mutUpdate = useMutation({
    mutationFn: ({ fid, req }: { fid: string; req: FactureRequest }) => updateFacture(fid, req),
    onSuccess: () => {
      toast.success(t("toastSaved"));
      qc.invalidateQueries({ queryKey: ["finance", "facture", id] });
      qc.invalidateQueries({ queryKey: ["finance", "factures"] });
      setEditOpen(false);
    },
  });

  const mutUploadJustif = useMutation({
    mutationFn: ({ fid, file }: { fid: string; file: File }) => uploadJustificatifFacture(fid, file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["finance", "facture", id] });
      qc.invalidateQueries({ queryKey: ["finance", "factures"] });
    },
  });

  const mutStatut = useMutation({
    mutationFn: ({ fid, s }: { fid: string; s: string }) => changerStatutFacture(fid, s),
    onSuccess: () => {
      toast.success(t("toastSaved"));
      qc.invalidateQueries({ queryKey: ["finance", "facture", id] });
      qc.invalidateQueries({ queryKey: ["finance", "factures"] });
    },
  });

  const mutPay = useMutation({
    mutationFn: (b: PaiementRequest) => enregistrerPaiement(b),
    onSuccess: () => {
      toast.success(t("toastPaymentSaved"));
      qc.invalidateQueries({ queryKey: ["finance", "facture", id] });
      qc.invalidateQueries({ queryKey: ["finance", "factures"] });
      qc.invalidateQueries({ queryKey: ["finance", "paiements"] });
      setPayOpen(false);
    },
  });

  if (isLoading || !detail) {
    return <p className="text-sm text-muted-foreground">{tc("loading")}</p>;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Link href="/finance/factures" className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}>
          {t("backToList")}
        </Link>
      </div>

      <div className="rounded-lg border border-border bg-card p-6 text-card-foreground shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold text-foreground">{detail.reference}</h1>
            <p className="text-sm text-muted-foreground">{detail.fournisseur}</p>
          </div>
          <Badge variant={statutBadge(detail.statut)}>{detail.statut}</Badge>
        </div>

        <dl className="mt-6 space-y-3 text-sm">
          <div className="grid grid-cols-2 gap-2">
            <dt className="text-muted-foreground">{t("fieldDate")}</dt>
            <dd>{detail.dateFacture}</dd>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <dt className="text-muted-foreground">{t("fieldTtc")}</dt>
            <dd>
              {fmt(detail.montantTtc)} {detail.devise} (≈ {fmt(detail.montantTtcEur)} EUR)
            </dd>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <dt className="text-muted-foreground">{t("fieldPaidRemaining")}</dt>
            <dd>
              {fmt(detail.montantPaye)} / {fmt(detail.montantRestant)}
            </dd>
          </div>
        </dl>

        {detail.justificatifUrl && (
          <a
            className="mt-4 inline-block text-sm text-indigo-600 hover:underline"
            href={detail.justificatifUrl}
            target="_blank"
            rel="noreferrer"
          >
            {t("viewJustificatif")}
          </a>
        )}

        {canEdit && (
          <div className="mt-8 flex flex-col gap-2 border-t border-border pt-6">
            {detail.statut === "A_PAYER" && parseFloat(String(detail.montantRestant)) > 0 && (
              <Button type="button" onClick={() => setPayOpen(true)}>
                {t("registerPayment")}
              </Button>
            )}
            {detail.statut === "BROUILLON" && (
              <Button type="button" variant="outline" onClick={() => mutStatut.mutate({ fid: detail.id, s: "A_PAYER" })}>
                {t("setToPay")}
              </Button>
            )}
            {(detail.statut === "BROUILLON" || detail.statut === "A_PAYER") && (
              <Button
                type="button"
                variant="outline"
                className="text-red-700"
                onClick={() => mutStatut.mutate({ fid: detail.id, s: "ANNULE" })}
              >
                {t("cancelInvoice")}
              </Button>
            )}
            {detail.statut !== "PAYE" && detail.statut !== "ANNULE" && (
              <Button type="button" variant="secondary" onClick={() => setEditOpen(true)}>
                {t("edit")}
              </Button>
            )}
          </div>
        )}
      </div>

      <FactureModal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        initialFacture={detail}
        categories={categories ?? []}
        onSubmit={async (req, file) => {
          await mutUpdate.mutateAsync({ fid: detail.id, req });
          if (file) {
            await mutUploadJustif.mutateAsync({ fid: detail.id, file });
          }
        }}
      />

      <PaiementModal
        open={payOpen}
        onClose={() => setPayOpen(false)}
        facture={detail}
        onSubmit={async (b) => {
          await mutPay.mutateAsync(b);
        }}
      />
    </div>
  );
}
