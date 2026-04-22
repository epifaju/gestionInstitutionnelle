"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { BienModal } from "@/components/inventaire/BienModal";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { BienRequest, BienResponse, MouvementResponse } from "@/lib/types/inventaire";
import { useAuthStore } from "@/lib/store";
import {
  creerBien,
  historiqueBien,
  listBiens,
  modifierBien,
  reformerBien,
  statsInventaire,
} from "@/services/inventaire.service";
import { listSalaries } from "@/services/salarie.service";

function fmtEur(n: string | number) {
  const x = typeof n === "number" ? n : parseFloat(String(n));
  return Number.isFinite(x)
    ? new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(x)
    : "—";
}

function etatVariant(e: string): "dangerSolid" | "warning" | "muted" {
  if (e === "HORS_SERVICE") return "dangerSolid";
  if (e === "DEFAILLANT") return "warning";
  return "muted";
}

export default function InventairePage() {
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const canWrite = user?.role === "ADMIN" || user?.role === "LOGISTIQUE";

  const [page, setPage] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [edit, setEdit] = useState<BienResponse | null>(null);
  const [histId, setHistId] = useState<string | null>(null);

  const { data: stats } = useQuery({ queryKey: ["inventaire", "stats"], queryFn: statsInventaire });
  const { data: salaries } = useQuery({
    queryKey: ["rh", "salaries", "all"],
    queryFn: () => listSalaries({ page: 0, size: 500 }),
  });
  const { data: listData, isLoading } = useQuery({
    queryKey: ["inventaire", "biens", page],
    queryFn: () => listBiens({ page, size: 20 }),
  });

  const { data: mouvements } = useQuery({
    queryKey: ["inventaire", "historique", histId],
    queryFn: () => historiqueBien(histId!),
    enabled: !!histId,
  });

  const mutSave = useMutation({
    mutationFn: async (body: { id?: string; req: BienRequest }) => {
      if (body.id) return modifierBien(body.id, body.req);
      return creerBien(body.req);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inventaire"] });
      setModalOpen(false);
      setEdit(null);
    },
  });

  const mutReforme = useMutation({
    mutationFn: ({ id, motif }: { id: string; motif: string }) => reformerBien(id, motif),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["inventaire"] }),
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Inventaire</h1>
          <p className="mt-1 text-sm text-slate-600">Biens matériels et mouvements.</p>
        </div>
        {canWrite && (
          <Button
            onClick={() => {
              setEdit(null);
              setModalOpen(true);
            }}
          >
            + Nouveau bien
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Valeur totale du parc (hors service exclu)</CardTitle>
        </CardHeader>
        <CardContent>
          {stats ? (
            <p className="text-3xl font-semibold tracking-tight text-slate-900">
              {fmtEur(stats.valeurTotaleParc)}
            </p>
          ) : (
            <Skeleton className="h-10 w-48" />
          )}
        </CardContent>
      </Card>

      {isLoading || !listData ? (
        <Skeleton className="h-64 w-full" />
      ) : (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Libellé</TableHead>
                  <TableHead>Catégorie</TableHead>
                  <TableHead>Localisation</TableHead>
                  <TableHead>État</TableHead>
                  <TableHead className="text-right">Valeur</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {listData.content.map((b) => (
                  <TableRow key={b.id}>
                    <TableCell className="font-mono text-xs">{b.codeInventaire}</TableCell>
                    <TableCell>{b.libelle}</TableCell>
                    <TableCell>{b.categorie}</TableCell>
                    <TableCell>{b.localisation ?? "—"}</TableCell>
                    <TableCell>
                      <Badge variant={etatVariant(b.etat)}>{b.etat}</Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{fmtEur(b.valeurAchat)}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex flex-wrap justify-end gap-1">
                        <Button size="sm" variant="outline" onClick={() => setHistId(b.id)}>
                          Historique
                        </Button>
                        {canWrite && (
                          <>
                            <Button
                              size="sm"
                              variant="secondary"
                              onClick={() => {
                                setEdit(b);
                                setModalOpen(true);
                              }}
                            >
                              Modifier
                            </Button>
                            <Button
                              size="sm"
                              variant="destructive"
                              onClick={() => {
                                const motif = window.prompt("Motif de réforme ?");
                                if (motif) mutReforme.mutate({ id: b.id, motif });
                              }}
                            >
                              Réformer
                            </Button>
                          </>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <div className="flex items-center justify-between text-sm">
        <Button variant="outline" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
          Précédent
        </Button>
        <span className="text-slate-600">
          Page {page + 1} / {Math.max(1, listData?.totalPages ?? 1)}
        </span>
        <Button
          variant="outline"
          disabled={listData ? listData.last : true}
          onClick={() => setPage((p) => p + 1)}
        >
          Suivant
        </Button>
      </div>

      <BienModal
        open={modalOpen}
        onClose={() => {
          setModalOpen(false);
          setEdit(null);
        }}
        initial={edit}
        salaries={salaries?.content ?? []}
        pending={mutSave.isPending}
        onSubmit={(req) => mutSave.mutate({ id: edit?.id, req })}
      />

      {histId && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/40">
          <div className="h-full w-full max-w-md overflow-y-auto bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold">Historique des mouvements</h2>
              <Button variant="ghost" size="sm" onClick={() => setHistId(null)}>
                Fermer
              </Button>
            </div>
            <ul className="mt-6 space-y-4 border-l-2 border-slate-200 pl-4">
              {(mouvements ?? []).map((m: MouvementResponse) => (
                <li key={m.id} className="relative">
                  <span className="absolute -left-[21px] top-1 h-3 w-3 rounded-full bg-slate-400" />
                  <p className="text-xs text-slate-500">
                    {m.dateMouvement ? new Date(m.dateMouvement).toLocaleString("fr-FR") : ""}
                  </p>
                  <p className="font-medium text-slate-900">
                    {m.typeMouvement}
                    {m.champModifie ? ` · ${m.champModifie}` : ""}
                  </p>
                  {(m.ancienneValeur || m.nouvelleValeur) && (
                    <p className="text-sm text-slate-600">
                      {m.ancienneValeur} → {m.nouvelleValeur}
                    </p>
                  )}
                  {m.motif && <p className="text-sm text-slate-500">Motif : {m.motif}</p>}
                  <p className="text-xs text-slate-400">{m.auteurNomComplet}</p>
                </li>
              ))}
            </ul>
            {!mouvements && <Skeleton className="mt-4 h-24" />}
          </div>
        </div>
      )}
    </div>
  );
}
