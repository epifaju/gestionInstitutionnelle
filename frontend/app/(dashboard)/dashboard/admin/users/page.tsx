"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { AdminUserResponse } from "@/lib/types/admin";
import { useAuthStore } from "@/lib/store";
import { createAdminUser, listAdminUsers, updateAdminUser } from "@/services/admin.service";
import { toast } from "sonner";

const ROLES = ["ADMIN", "FINANCIER", "RH", "LOGISTIQUE", "EMPLOYE"] as const;

function UserModal({
  open,
  onClose,
  editing,
}: {
  open: boolean;
  onClose: () => void;
  editing: AdminUserResponse | null;
}) {
  const qc = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [nom, setNom] = useState("");
  const [prenom, setPrenom] = useState("");
  const [role, setRole] = useState<string>("EMPLOYE");
  const [actif, setActif] = useState(true);
  const [pwdEdit, setPwdEdit] = useState("");

  useEffect(() => {
    if (!open) return;
    if (editing) {
      setEmail(editing.email);
      setPassword("");
      setNom(editing.nom ?? "");
      setPrenom(editing.prenom ?? "");
      setRole(editing.role);
      setActif(editing.actif);
      setPwdEdit("");
    } else {
      setEmail("");
      setPassword("");
      setNom("");
      setPrenom("");
      setRole("EMPLOYE");
      setActif(true);
      setPwdEdit("");
    }
  }, [open, editing]);

  const mutCreate = useMutation({
    mutationFn: () =>
      createAdminUser({
        email,
        password,
        nom: nom || null,
        prenom: prenom || null,
        role,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "users"] });
      toast.success("Utilisateur créé");
      onClose();
    },
    onError: (e: unknown) => {
      const msg = e && typeof e === "object" && "message" in e ? String((e as { message: unknown }).message) : "Erreur";
      toast.error(msg);
    },
  });

  const mutUpdate = useMutation({
    mutationFn: () => {
      if (!editing) return Promise.reject();
      const body: Parameters<typeof updateAdminUser>[1] = {
        nom: nom || null,
        prenom: prenom || null,
        role,
        actif,
      };
      if (pwdEdit.trim().length >= 8) body.password = pwdEdit;
      return updateAdminUser(editing.id, body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "users"] });
      toast.success("Utilisateur mis à jour");
      onClose();
    },
    onError: (e: unknown) => {
      const msg = e && typeof e === "object" && "message" in e ? String((e as { message: unknown }).message) : "Erreur";
      toast.error(msg);
    },
  });

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg bg-white p-4 shadow-xl">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">{editing ? "Modifier l’utilisateur" : "Nouvel utilisateur"}</h2>
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            Fermer
          </Button>
        </div>
        <div className="grid gap-3">
          <div>
            <Label htmlFor="em">Email</Label>
            <Input
              id="em"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={!!editing}
              autoComplete="off"
            />
          </div>
          {!editing && (
            <div>
              <Label htmlFor="pw">Mot de passe (min. 8)</Label>
              <Input
                id="pw"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
              />
            </div>
          )}
          <div>
            <Label htmlFor="nom">Nom</Label>
            <Input id="nom" value={nom} onChange={(e) => setNom(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="pre">Prénom</Label>
            <Input id="pre" value={prenom} onChange={(e) => setPrenom(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="rl">Rôle</Label>
            <select
              id="rl"
              className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-2 text-sm"
              value={role}
              onChange={(e) => setRole(e.target.value)}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>
          {editing && (
            <>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={actif} onChange={(e) => setActif(e.target.checked)} />
                Compte actif
              </label>
              <div>
                <Label htmlFor="npw">Nouveau mot de passe (optionnel, min. 8)</Label>
                <Input
                  id="npw"
                  type="password"
                  value={pwdEdit}
                  onChange={(e) => setPwdEdit(e.target.value)}
                  autoComplete="new-password"
                />
              </div>
            </>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Annuler
            </Button>
            {editing ? (
              <Button
                type="button"
                onClick={() => mutUpdate.mutate()}
                disabled={mutUpdate.isPending}
              >
                Enregistrer
              </Button>
            ) : (
              <Button
                type="button"
                onClick={() => mutCreate.mutate()}
                disabled={mutCreate.isPending || !email || password.length < 8}
              >
                Créer
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function AdminUsersPage() {
  const user = useAuthStore((s) => s.user);
  const [page, setPage] = useState(0);
  const [modal, setModal] = useState(false);
  const [editing, setEditing] = useState<AdminUserResponse | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "users", page],
    queryFn: () => listAdminUsers({ page, size: 20 }),
    enabled: user?.role === "ADMIN",
  });

  if (user?.role !== "ADMIN") {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        Accès réservé aux administrateurs.
      </div>
    );
  }

  const rows = data?.content ?? [];

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Utilisateurs</h1>
          <p className="text-sm text-slate-600">Comptes de l’organisation (rôle ADMIN requis).</p>
        </div>
        <Button
          type="button"
          onClick={() => {
            setEditing(null);
            setModal(true);
          }}
        >
          + Nouvel utilisateur
        </Button>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Email</TableHead>
              <TableHead>Nom</TableHead>
              <TableHead>Rôle</TableHead>
              <TableHead>Statut</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5}>Chargement…</TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5}>Aucun utilisateur</TableCell>
              </TableRow>
            ) : (
              rows.map((u) => (
                <TableRow key={u.id}>
                  <TableCell className="font-medium">{u.email}</TableCell>
                  <TableCell>
                    {u.prenom} {u.nom}
                  </TableCell>
                  <TableCell>{u.role}</TableCell>
                  <TableCell>
                    <Badge variant={u.actif ? "success" : "muted"}>{u.actif ? "Actif" : "Inactif"}</Badge>
                  </TableCell>
                  <TableCell>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        setEditing(u);
                        setModal(true);
                      }}
                    >
                      Modifier
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-600">
          <span>
            Page {data.page + 1} / {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" disabled={data.page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Précédent
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Suivant
            </Button>
          </div>
        </div>
      )}

      <UserModal
        open={modal}
        editing={editing}
        onClose={() => {
          setModal(false);
          setEditing(null);
        }}
      />
    </div>
  );
}
