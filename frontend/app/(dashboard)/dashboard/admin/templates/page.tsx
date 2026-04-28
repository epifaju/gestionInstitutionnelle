"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { TemplateCategory, TemplateDefinitionDto, TemplateFormat, TemplateRevisionDto, TemplateStatus } from "@/services/template-admin.service";
import {
  createTemplateAdmin,
  listTemplateRevisionsAdmin,
  listTemplatesAdmin,
  updateTemplateAdmin,
  uploadTemplateRevisionAdmin,
} from "@/services/template-admin.service";

function upper(s: string) {
  return (s ?? "").trim().toUpperCase();
}

function isOneOf<T extends readonly string[]>(arr: T, v: string): v is T[number] {
  return (arr as readonly string[]).includes(v);
}

export default function AdminTemplatesPage() {
  const t = useTranslations("Admin.templates");
  const tc = useTranslations("Common");
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "templates"],
    queryFn: listTemplatesAdmin,
  });

  const templates = data ?? [];

  const [createOpen, setCreateOpen] = useState(false);
  const [code, setCode] = useState("");
  const [label, setLabel] = useState("");
  const [category, setCategory] = useState<TemplateCategory>("MISSION");
  const [format, setFormat] = useState<TemplateFormat>("DOCX");

  const [editing, setEditing] = useState<TemplateDefinitionDto | null>(null);
  const [editLabel, setEditLabel] = useState("");
  const [editStatus, setEditStatus] = useState<TemplateStatus>("DRAFT");

  const mutCreate = useMutation({
    mutationFn: () =>
      createTemplateAdmin({
        code: upper(code),
        label: label.trim(),
        category,
        format,
        defaultLocale: null,
      }),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["admin", "templates"] });
      setCreateOpen(false);
      setCode("");
      setLabel("");
    },
  });

  const mutUpdate = useMutation({
    mutationFn: () => {
      if (!editing) throw new Error("no editing");
      return updateTemplateAdmin(editing.id, { label: editLabel.trim(), status: editStatus, defaultLocale: null });
    },
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["admin", "templates"] });
      setEditing(null);
    },
  });

  const [revisionsFor, setRevisionsFor] = useState<TemplateDefinitionDto | null>(null);
  const { data: revisions } = useQuery({
    queryKey: ["admin", "templates", "revisions", revisionsFor?.id],
    queryFn: () => (revisionsFor ? listTemplateRevisionsAdmin(revisionsFor.id) : Promise.resolve([] as TemplateRevisionDto[])),
    enabled: !!revisionsFor,
  });

  const mutUpload = useMutation({
    mutationFn: async (args: { templateId: string; file: File }) => uploadTemplateRevisionAdmin(args.templateId, args.file, null),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["admin", "templates", "revisions", revisionsFor?.id] });
    },
  });

  const categories = useMemo(() => ["MISSION", "FRAIS", "CONTRAT", "COURRIER", "PV"] as const, []);
  const formats = useMemo(() => ["DOCX", "HTML"] as const, []);
  const statuses = useMemo(() => ["DRAFT", "ACTIVE", "DISABLED"] as const, []);

  if (isLoading) return <div>{tc("loading")}</div>;

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <Button type="button" onClick={() => setCreateOpen((v) => !v)}>
          + {t("new")}
        </Button>
      </div>

      {createOpen ? (
        <Card className="p-4 space-y-3">
          <p className="text-sm font-semibold text-foreground">{t("createTitle")}</p>
          <div className="grid gap-3 md:grid-cols-4">
            <div className="space-y-1">
              <Label>{t("code")}</Label>
              <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="MISSION_ORDRE" />
            </div>
            <div className="space-y-1 md:col-span-2">
              <Label>{t("label")}</Label>
              <Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder={t("labelPh")} />
            </div>
            <div className="space-y-1">
              <Label>{t("category")}</Label>
              <select
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={category}
                onChange={(e) => {
                  const v = e.target.value;
                  if (isOneOf(categories, v)) setCategory(v);
                }}
              >
                {categories.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label>{t("format")}</Label>
              <select
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={format}
                onChange={(e) => {
                  const v = e.target.value;
                  if (isOneOf(formats, v)) setFormat(v);
                }}
              >
                {formats.map((f) => (
                  <option key={f} value={f}>
                    {f}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
              {tc("cancel")}
            </Button>
            <Button
              type="button"
              disabled={mutCreate.isPending || !upper(code) || !label.trim()}
              onClick={() => mutCreate.mutate()}
            >
              {tc("save")}
            </Button>
          </div>
        </Card>
      ) : null}

      <Card className="p-4">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("thCode")}</TableHead>
              <TableHead>{t("thLabel")}</TableHead>
              <TableHead>{t("thCategory")}</TableHead>
              <TableHead>{t("thFormat")}</TableHead>
              <TableHead>{t("thStatus")}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {templates.map((tpl) => (
              <TableRow key={tpl.id}>
                <TableCell className="font-medium">{tpl.code}</TableCell>
                <TableCell>{tpl.label}</TableCell>
                <TableCell>{tpl.category}</TableCell>
                <TableCell>{tpl.format}</TableCell>
                <TableCell>{tpl.status}</TableCell>
                <TableCell className="text-right space-x-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      setEditing(tpl);
                      setEditLabel(tpl.label);
                      setEditStatus(tpl.status);
                    }}
                  >
                    {t("edit")}
                  </Button>
                  <Button type="button" variant="outline" size="sm" onClick={() => setRevisionsFor(tpl)}>
                    {t("revisions")}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      {editing ? (
        <Card className="p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div>
              <p className="text-sm font-semibold text-foreground">{t("editTitle", { code: editing.code })}</p>
              <p className="text-xs text-muted-foreground">{t("editHint")}</p>
            </div>
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={() => setEditing(null)}>
                {tc("cancel")}
              </Button>
              <Button type="button" disabled={mutUpdate.isPending} onClick={() => mutUpdate.mutate()}>
                {tc("save")}
              </Button>
            </div>
          </div>

          <div className="grid gap-3 md:grid-cols-3">
            <div className="space-y-1 md:col-span-2">
              <Label>{t("label")}</Label>
              <Input value={editLabel} onChange={(e) => setEditLabel(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label>{t("status")}</Label>
              <select
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={editStatus}
                onChange={(e) => {
                  const v = e.target.value;
                  if (isOneOf(statuses, v)) setEditStatus(v);
                }}
              >
                {statuses.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </Card>
      ) : null}

      {revisionsFor ? (
        <Card className="p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div>
              <p className="text-sm font-semibold text-foreground">{t("revisionsTitle", { code: revisionsFor.code })}</p>
              <p className="text-xs text-muted-foreground">{t("revisionsHint")}</p>
            </div>
            <Button type="button" variant="outline" onClick={() => setRevisionsFor(null)}>
              {tc("close")}
            </Button>
          </div>

          <div className="flex items-center justify-between gap-2">
            <Label className="text-sm">{t("upload")}</Label>
            <input
              type="file"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (!f || !revisionsFor) return;
                mutUpload.mutate({ templateId: revisionsFor.id, file: f });
                e.currentTarget.value = "";
              }}
            />
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("thVersion")}</TableHead>
                <TableHead>{t("thMime")}</TableHead>
                <TableHead>{t("thCreatedAt")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(revisions ?? []).map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="font-medium">v{r.version}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{r.contentMime ?? "-"}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{r.createdAt}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      ) : null}
    </div>
  );
}

