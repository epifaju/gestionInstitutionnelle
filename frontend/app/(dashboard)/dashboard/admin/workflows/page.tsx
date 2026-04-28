"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { WorkflowDefinitionDto, WorkflowRuleDto } from "@/services/workflow-admin.service";
import { listWorkflowDefinitions, upsertWorkflowDefinition } from "@/services/workflow-admin.service";

const PROCESS_KEYS = ["MISSION_APPROVAL", "FRAIS_VALIDATE", "FRAIS_REIMBURSE", "FACTURE_APPROVAL", "CONGE_APPROVAL"] as const;

function num(v: unknown) {
  if (v == null) return null;
  const n = typeof v === "number" ? v : parseFloat(String(v));
  return Number.isFinite(n) ? n : null;
}

export default function AdminWorkflowsPage() {
  const t = useTranslations("Admin.workflows");
  const tc = useTranslations("Common");
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "workflows"],
    queryFn: listWorkflowDefinitions,
  });

  const defs = useMemo(() => {
    const map = new Map<string, WorkflowDefinitionDto>();
    (data ?? []).forEach((d) => map.set(d.processKey, d));
    return PROCESS_KEYS.map((k) => map.get(k) ?? { id: "", processKey: k, enabled: false, rules: [] });
  }, [data]);

  const [editing, setEditing] = useState<WorkflowDefinitionDto | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [rules, setRules] = useState<Array<Omit<WorkflowRuleDto, "id"> & { id?: string }>>([]);

  useEffect(() => {
    if (!editing) return;
    setEnabled(editing.enabled);
    setRules(
      (editing.rules ?? []).map((r) => ({
        id: r.id,
        minAmountEur: r.minAmountEur,
        maxAmountEur: r.maxAmountEur,
        levels: r.levels,
        level1Role: r.level1Role,
        level2Role: r.level2Role,
      }))
    );
  }, [editing]);

  const mutSave = useMutation({
    mutationFn: async () => {
      if (!editing) throw new Error("No process key");
      return upsertWorkflowDefinition({
        processKey: editing.processKey,
        enabled,
        rules: rules.map((r) => ({
          minAmountEur: num(r.minAmountEur),
          maxAmountEur: num(r.maxAmountEur),
          levels: Number(r.levels) || 1,
          level1Role: String(r.level1Role || "").trim(),
          level2Role: r.level2Role ? String(r.level2Role).trim() : null,
        })),
      });
    },
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["admin", "workflows"] });
      setEditing(null);
    },
  });

  if (isLoading) return <div>{tc("loading")}</div>;

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
      </div>

      <Card className="p-4">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("thProcess")}</TableHead>
              <TableHead>{t("thEnabled")}</TableHead>
              <TableHead>{t("thRules")}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {defs.map((d) => (
              <TableRow key={d.processKey}>
                <TableCell className="font-medium">{d.processKey}</TableCell>
                <TableCell>{d.enabled ? t("enabledYes") : t("enabledNo")}</TableCell>
                <TableCell className="text-sm text-muted-foreground">{(d.rules ?? []).length}</TableCell>
                <TableCell className="text-right">
                  <Button type="button" variant="outline" size="sm" onClick={() => setEditing(d)}>
                    {t("edit")}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      {editing ? (
        <Card className="p-4 space-y-4">
          <div className="flex items-center justify-between gap-2">
            <div>
              <p className="text-sm font-semibold text-foreground">{t("editTitle", { key: editing.processKey })}</p>
              <p className="text-xs text-muted-foreground">{t("editHint")}</p>
            </div>
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={() => setEditing(null)}>
                {tc("cancel")}
              </Button>
              <Button type="button" disabled={mutSave.isPending} onClick={() => mutSave.mutate()}>
                {tc("save")}
              </Button>
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            {t("enabledLabel")}
          </label>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <p className="text-sm font-semibold text-foreground">{t("rulesTitle")}</p>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() =>
                  setRules((x) => [
                    ...x,
                    { minAmountEur: null, maxAmountEur: null, levels: 1, level1Role: "RH", level2Role: null },
                  ])
                }
              >
                + {t("addRule")}
              </Button>
            </div>

            {rules.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("rulesEmpty")}</p>
            ) : (
              <div className="space-y-2">
                {rules.map((r, idx) => (
                  <div key={idx} className="grid gap-2 md:grid-cols-6 items-end rounded-md border border-border p-3">
                    <div className="space-y-1">
                      <Label>{t("min")}</Label>
                      <Input
                        type="number"
                        value={r.minAmountEur ?? ""}
                        onChange={(e) => {
                          const v = e.target.value;
                          setRules((arr) => arr.map((it, i) => (i === idx ? { ...it, minAmountEur: v } : it)));
                        }}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label>{t("max")}</Label>
                      <Input
                        type="number"
                        value={r.maxAmountEur ?? ""}
                        onChange={(e) => {
                          const v = e.target.value;
                          setRules((arr) => arr.map((it, i) => (i === idx ? { ...it, maxAmountEur: v } : it)));
                        }}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label>{t("levels")}</Label>
                      <Input
                        type="number"
                        min={1}
                        max={2}
                        value={r.levels}
                        onChange={(e) => {
                          const v = Number(e.target.value);
                          setRules((arr) => arr.map((it, i) => (i === idx ? { ...it, levels: v } : it)));
                        }}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label>{t("role1")}</Label>
                      <Input
                        value={r.level1Role ?? ""}
                        onChange={(e) => {
                          const v = e.target.value;
                          setRules((arr) => arr.map((it, i) => (i === idx ? { ...it, level1Role: v } : it)));
                        }}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label>{t("role2")}</Label>
                      <Input
                        value={r.level2Role ?? ""}
                        disabled={Number(r.levels) !== 2}
                        onChange={(e) => {
                          const v = e.target.value;
                          setRules((arr) => arr.map((it, i) => (i === idx ? { ...it, level2Role: v } : it)));
                        }}
                      />
                    </div>
                    <div className="flex justify-end">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => setRules((arr) => arr.filter((_, i) => i !== idx))}
                      >
                        {t("remove")}
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Card>
      ) : null}
    </div>
  );
}

