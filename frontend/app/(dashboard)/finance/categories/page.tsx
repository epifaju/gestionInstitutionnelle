"use client";

import axios from "axios";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { CategorieRequest, CategorieResponse } from "@/lib/types/finance";
import { useAuthStore } from "@/lib/store";
import { createCategorie, deleteCategorie, listCategoriesAdmin, reactivateCategorie, updateCategorie } from "@/services/finance.service";

const TYPES = ["DEPENSE", "RECETTE"] as const;

function normalizeCode(v: string) {
  return v
    .trim()
    .toUpperCase()
    .replace(/\s+/g, "_")
    .replace(/[^A-Z0-9_]/g, "");
}

export default function FinanceCategoriesPage() {
  const t = useTranslations("Finance.categories");
  const tc = useTranslations("Common");
  const qc = useQueryClient();
  const role = useAuthStore((s) => s.user?.role);
  const isAdmin = role === "ADMIN";
  const isFin = role === "FINANCIER";
  const canManage = isAdmin || isFin;

  const [showInactive, setShowInactive] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ["finance", "categories", { includeInactive: showInactive }],
    queryFn: () => listCategoriesAdmin({ includeInactive: showInactive }),
  });

  const [form, setForm] = useState<CategorieRequest>({
    libelle: "",
    code: "",
    type: "DEPENSE",
    couleur: "#6B7280",
  });

  const mut = useMutation({
    mutationFn: (b: CategorieRequest) => createCategorie(b),
    onSuccess: async () => {
      toast.success(t("created"));
      setForm((f) => ({ ...f, libelle: "", code: "" }));
      await qc.invalidateQueries({ queryKey: ["finance", "categories"] });
    },
  });

  const [editOpen, setEditOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<CategorieRequest>({
    libelle: "",
    code: "",
    type: "DEPENSE",
    couleur: "#6B7280",
  });

  const mutEdit = useMutation({
    mutationFn: async () => {
      if (!editId) throw new Error("Missing id");
      return updateCategorie(editId, { ...editForm, libelle: editForm.libelle.trim(), code: editForm.code.trim() });
    },
    onSuccess: async () => {
      toast.success(t("updated") ?? "Catégorie modifiée");
      setEditOpen(false);
      setEditId(null);
      await qc.invalidateQueries({ queryKey: ["finance", "categories"] });
    },
  });

  const mutDelete = useMutation({
    mutationFn: (id: string) => deleteCategorie(id),
    onSuccess: async () => {
      toast.success(t("deleted") ?? "Catégorie supprimée");
      setDeleteOpen(false);
      setDeleteTarget(null);
      await qc.invalidateQueries({ queryKey: ["finance", "categories"] });
    },
  });

  const mutReactivate = useMutation({
    mutationFn: (id: string) => reactivateCategorie(id),
    onSuccess: async () => {
      toast.success("Catégorie réactivée");
      await qc.invalidateQueries({ queryKey: ["finance", "categories"] });
    },
  });

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<CategorieResponse | null>(null);

  function openEdit(c: CategorieResponse) {
    setEditId(c.id);
    setEditForm({
      libelle: c.libelle ?? "",
      code: c.code ?? "",
      type: (c.type as "DEPENSE" | "RECETTE") ?? "DEPENSE",
      couleur: c.couleur ?? "#6B7280",
    });
    setEditOpen(true);
  }

  function openDelete(c: CategorieResponse) {
    setDeleteTarget(c);
    setDeleteOpen(true);
  }

  const rows = useMemo(() => (data ?? []) as CategorieResponse[], [data]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <div className="rounded-lg border border-border bg-card p-4 text-card-foreground">
        <h2 className="text-sm font-semibold text-foreground">{t("createTitle")}</h2>
        <div className="mt-3 grid gap-3 md:grid-cols-4">
          <div className="md:col-span-2">
            <Label>{t("libelle")}</Label>
            <Input
              value={form.libelle}
              onChange={(e) => {
                const lib = e.target.value;
                setForm((f) => ({
                  ...f,
                  libelle: lib,
                  code: f.code ? f.code : normalizeCode(lib),
                }));
              }}
            />
          </div>
          <div>
            <Label>{t("code")}</Label>
            <Input value={form.code} onChange={(e) => setForm((f) => ({ ...f, code: normalizeCode(e.target.value) }))} />
          </div>
          <div>
            <Label>{t("type")}</Label>
            <select
              className="flex h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground"
              value={form.type}
              onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
            >
              {TYPES.map((x) => (
                <option key={x} value={x}>
                  {x}
                </option>
              ))}
            </select>
          </div>
          <div>
            <Label>{t("couleur")}</Label>
            <Input type="color" value={form.couleur ?? "#6B7280"} onChange={(e) => setForm((f) => ({ ...f, couleur: e.target.value }))} />
          </div>
          <div className="md:col-span-3" />
          <div className="flex items-end justify-end">
            <Button
              type="button"
              disabled={!canManage || mut.isPending}
              onClick={() => mut.mutate({ ...form, libelle: form.libelle.trim(), code: form.code.trim() })}
            >
              {t("createButton")}
            </Button>
          </div>
        </div>
        {!canManage && (
          <p className="mt-2 text-sm text-muted-foreground">
            Les catégories ne peuvent être modifiées que par un Administrateur ou un Financier.
          </p>
        )}
      </div>

      <div className="rounded-lg border border-border bg-card text-card-foreground">
        {canManage && (
          <div className="flex items-center justify-between gap-3 border-b border-border p-3 text-sm">
            <label className="inline-flex items-center gap-2">
              <input
                type="checkbox"
                checked={showInactive}
                onChange={(e) => setShowInactive(e.target.checked)}
              />
              <span>Afficher les inactives</span>
            </label>
            <span className="text-xs text-muted-foreground">Les catégories supprimées sont marquées inactives.</span>
          </div>
        )}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("thLibelle")}</TableHead>
              <TableHead>{t("thCode")}</TableHead>
              <TableHead>{t("thType")}</TableHead>
              <TableHead>{t("thCouleur")}</TableHead>
              <TableHead>{t("thActif")}</TableHead>
              {canManage && <TableHead className="text-right">Actions</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={canManage ? 6 : 5}>{tc("loading")}</TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={canManage ? 6 : 5}>{t("empty")}</TableCell>
              </TableRow>
            ) : (
              rows.map((c) => (
                <TableRow key={c.id}>
                  <TableCell className="font-medium">{c.libelle}</TableCell>
                  <TableCell>{c.code}</TableCell>
                  <TableCell>{c.type}</TableCell>
                  <TableCell>
                    <span className="inline-flex items-center gap-2">
                      <span className="h-3 w-3 rounded-full" style={{ background: c.couleur }} />
                      <span className="text-xs text-muted-foreground">{c.couleur}</span>
                    </span>
                  </TableCell>
                  <TableCell>{c.actif ? t("yes") : t("no")}</TableCell>
                  {canManage && (
                    <TableCell className="text-right">
                      <div className="inline-flex items-center gap-2">
                        <Button type="button" size="sm" variant="outline" onClick={() => openEdit(c)}>
                          {tc("modify")}
                        </Button>
                        {c.actif ? (
                          <Button
                            type="button"
                            size="sm"
                            variant="destructive"
                            disabled={mutDelete.isPending}
                            onClick={() => openDelete(c)}
                          >
                            {tc("delete")}
                          </Button>
                        ) : (
                          <Button
                            type="button"
                            size="sm"
                            variant="secondary"
                            disabled={mutReactivate.isPending}
                            onClick={() => mutReactivate.mutate(c.id)}
                          >
                            Réactiver
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {editOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-card p-6 text-card-foreground shadow-lg">
            <h2 className="text-lg font-semibold">Modifier la catégorie</h2>
            <div className="mt-4 space-y-3">
              <div>
                <Label>{t("libelle")}</Label>
                <Input
                  value={editForm.libelle}
                  onChange={(e) => {
                    const lib = e.target.value;
                    setEditForm((f) => ({
                      ...f,
                      libelle: lib,
                      code: f.code ? f.code : normalizeCode(lib),
                    }));
                  }}
                />
              </div>
              <div>
                <Label>{t("code")}</Label>
                <Input value={editForm.code} onChange={(e) => setEditForm((f) => ({ ...f, code: normalizeCode(e.target.value) }))} />
              </div>
              <div>
                <Label>{t("type")}</Label>
                <select
                  className="flex h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground"
                  value={editForm.type}
                  onChange={(e) => setEditForm((f) => ({ ...f, type: e.target.value }))}
                >
                  {TYPES.map((x) => (
                    <option key={x} value={x}>
                      {x}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <Label>{t("couleur")}</Label>
                <Input type="color" value={editForm.couleur ?? "#6B7280"} onChange={(e) => setEditForm((f) => ({ ...f, couleur: e.target.value }))} />
              </div>
              {mutEdit.isError && (
                <p className="text-sm text-red-600">
                  {axios.isAxiosError(mutEdit.error)
                    ? ((mutEdit.error.response?.data as { message?: string; code?: string } | undefined)?.message ?? "Erreur")
                    : "Erreur"}
                </p>
              )}
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="outline" type="button" onClick={() => setEditOpen(false)}>
                Annuler
              </Button>
              <Button type="button" disabled={mutEdit.isPending} onClick={() => mutEdit.mutate()}>
                Enregistrer
              </Button>
            </div>
          </div>
        </div>
      )}

      {deleteOpen && deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-card p-6 text-card-foreground shadow-lg">
            <h2 className="text-lg font-semibold text-foreground">Supprimer la catégorie</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Cette action va <span className="font-medium">désactiver</span> la catégorie{" "}
              <span className="font-semibold text-foreground">“{deleteTarget.libelle}”</span>. Elle pourra être réactivée
              plus tard via “Afficher les inactives”.
            </p>

            {mutDelete.isError && (
              <p className="mt-3 text-sm text-red-600">
                {axios.isAxiosError(mutDelete.error)
                  ? ((mutDelete.error.response?.data as { message?: string; code?: string } | undefined)?.message ??
                      "Erreur")
                  : "Erreur"}
              </p>
            )}

            <div className="mt-6 flex justify-end gap-2">
              <Button
                variant="outline"
                type="button"
                disabled={mutDelete.isPending}
                onClick={() => {
                  setDeleteOpen(false);
                  setDeleteTarget(null);
                }}
              >
                Annuler
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={mutDelete.isPending}
                onClick={() => mutDelete.mutate(deleteTarget.id)}
              >
                {mutDelete.isPending ? "Suppression…" : "Supprimer"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

