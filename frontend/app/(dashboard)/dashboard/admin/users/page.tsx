"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
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
  const t = useTranslations("Admin.users");
  const tc = useTranslations("Common");
  const tv = useTranslations("Validation");
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
      toast.success(t("toastCreated"));
      onClose();
    },
    onError: (e: unknown) => {
      const msg =
        e && typeof e === "object" && "message" in e
          ? String((e as { message: unknown }).message)
          : tc("errorGeneric");
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
      toast.success(t("toastUpdated"));
      onClose();
    },
    onError: (e: unknown) => {
      const msg =
        e && typeof e === "object" && "message" in e
          ? String((e as { message: unknown }).message)
          : tc("errorGeneric");
      toast.error(msg);
    },
  });

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg bg-card p-4 text-card-foreground shadow-xl">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">{editing ? t("modalTitleEdit") : t("modalTitleNew")}</h2>
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            {tc("close")}
          </Button>
        </div>
        <div className="grid gap-3">
          <div>
            <Label htmlFor="em">{t("email")}</Label>
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
              <Label htmlFor="pw">{t("passwordNew")}</Label>
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
            <Label htmlFor="nom">{t("nom")}</Label>
            <Input id="nom" value={nom} onChange={(e) => setNom(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="pre">{t("prenom")}</Label>
            <Input id="pre" value={prenom} onChange={(e) => setPrenom(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="rl">{t("role")}</Label>
            <select
              id="rl"
              className="flex h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground"
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
                {t("accountActive")}
              </label>
              <div>
                <Label htmlFor="npw">{t("passwordEditOptional")}</Label>
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
              {tc("cancel")}
            </Button>
            {editing ? (
              <Button
                type="button"
                onClick={() => mutUpdate.mutate()}
                disabled={mutUpdate.isPending}
              >
                {tc("save")}
              </Button>
            ) : (
              <Button
                type="button"
                onClick={() => {
                  if (!email.trim()) {
                    toast.error(`${t("email")} — ${tv("required")}`);
                    return;
                  }
                  if (password.length < 8) {
                    toast.error(`${t("passwordNew")} — ${tv("minLength")}`);
                    return;
                  }
                  mutCreate.mutate();
                }}
                disabled={mutCreate.isPending}
              >
                {tc("create")}
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function AdminUsersPage() {
  const t = useTranslations("Admin.users");
  const tc = useTranslations("Common");
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
        {t("restricted")}
      </div>
    );
  }

  const rows = data?.content ?? [];

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <Button
          type="button"
          onClick={() => {
            setEditing(null);
            setModal(true);
          }}
        >
          {t("create")}
        </Button>
      </div>

      <div className="rounded-lg border border-border bg-card text-card-foreground">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("thEmail")}</TableHead>
              <TableHead>{t("thNom")}</TableHead>
              <TableHead>{t("thRole")}</TableHead>
              <TableHead>{t("thStatut")}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5}>{tc("loading")}</TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5}>{t("empty")}</TableCell>
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
                    <Badge variant={u.actif ? "success" : "muted"}>{u.actif ? t("active") : t("inactive")}</Badge>
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
                      {tc("modify")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>
            {tc("page", { current: data.page + 1, total: data.totalPages })}
          </span>
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" disabled={data.page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              {tc("previous")}
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              {tc("next")}
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
