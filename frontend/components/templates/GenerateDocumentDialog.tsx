"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import type { TemplateAvailableDto, TemplateOutputFormat } from "@/services/template-catalog.service";
import { listAvailableTemplates } from "@/services/template-catalog.service";
import { generateFromTemplate } from "@/services/template.service";
import { getPresignedUrl } from "@/services/documents.service";

function outputsLabel(t: ReturnType<typeof useTranslations>, o: TemplateOutputFormat) {
  if (o === "PDF") return t("pdf");
  if (o === "DOCX") return t("docx");
  return t("html");
}

export function GenerateDocumentDialog({
  subjectType,
  subjectId,
  triggerLabel,
}: {
  subjectType: string;
  subjectId: string;
  triggerLabel?: string;
}) {
  const t = useTranslations("Templates.generate");
  const tc = useTranslations("Common");

  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [selectedCode, setSelectedCode] = useState<string>("");
  const [output, setOutput] = useState<TemplateOutputFormat>("PDF");

  const { data, isLoading, refetch } = useQuery({
    queryKey: ["templates", "catalog", subjectType],
    queryFn: () => listAvailableTemplates(subjectType),
    enabled: false,
  });

  useEffect(() => {
    if (!open) return;
    refetch();
  }, [open, refetch]);

  const list = useMemo(() => {
    const arr = (data ?? []) as TemplateAvailableDto[];
    const q = query.trim().toLowerCase();
    if (!q) return arr;
    return arr.filter((x) => x.code.toLowerCase().includes(q) || x.label.toLowerCase().includes(q));
  }, [data, query]);

  const selected = useMemo(() => list.find((x) => x.code === selectedCode) ?? null, [list, selectedCode]);
  const canGenerate = !!selectedCode && !!selected?.hasRevision;

  useEffect(() => {
    if (!open) return;
    if (!selectedCode && list.length) {
      setSelectedCode(list[0].code);
    }
  }, [open, selectedCode, list]);

  useEffect(() => {
    if (!open) return;
    if (!selected) return;
    if (!selected.allowedOutputs.includes(output)) {
      setOutput(selected.allowedOutputs[0] ?? "PDF");
    }
  }, [open, selected, output]);

  const mutGenerate = useMutation({
    mutationFn: async () => {
      if (!selectedCode) throw new Error("No template selected");
      if (selected && !selected.hasRevision) throw new Error("Template has no revision");
      return generateFromTemplate(selectedCode, { subjectType, subjectId, outputFormat: output });
    },
    onSuccess: async (gd) => {
      toast.success(tc("successCreated"));
      setOpen(false);
      if (gd.outputDocumentId) {
        const { url } = await getPresignedUrl(gd.outputDocumentId);
        window.open(url, "_blank", "noopener,noreferrer");
      }
    },
  });

  return (
    <>
      <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)}>
        {triggerLabel ?? t("trigger")}
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t("title")}</DialogTitle>
            <DialogDescription>{t("subtitle")}</DialogDescription>
          </DialogHeader>

          <div className="grid gap-3 md:grid-cols-2">
            <div className="space-y-1">
              <Label>{t("search")}</Label>
              <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t("searchPh")} />
            </div>
            <div className="space-y-1">
              <Label>{t("output")}</Label>
              <select
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={output}
                onChange={(e) => setOutput(e.target.value as TemplateOutputFormat)}
                disabled={!selected}
              >
                {(selected?.allowedOutputs ?? ["PDF", "DOCX", "HTML"]).map((o) => (
                  <option key={o} value={o}>
                    {outputsLabel(t, o)}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="space-y-2">
            <p className="text-sm font-semibold text-foreground">{t("templates")}</p>
            {isLoading ? (
              <p className="text-sm text-muted-foreground">{tc("loading")}</p>
            ) : list.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("empty")}</p>
            ) : selected && !selected.hasRevision ? (
              <p className="text-sm text-amber-700 dark:text-amber-400">{t("missingRevision")}</p>
            ) : (
              <div className="max-h-64 overflow-auto rounded-md border border-border">
                {list.map((x) => (
                  <button
                    key={x.code}
                    type="button"
                    className={`flex w-full items-start justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-muted ${
                      x.code === selectedCode ? "bg-muted" : ""
                    }`}
                    onClick={() => setSelectedCode(x.code)}
                  >
                    <div className="min-w-0">
                      <div className="font-medium text-foreground">
                        {x.label}{" "}
                        {!x.hasRevision ? <span className="ml-2 text-xs text-amber-700 dark:text-amber-400">({t("noRevisionBadge")})</span> : null}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {x.code}
                        {x.latestVersion ? <span className="ml-2">· v{x.latestVersion}</span> : null}
                      </div>
                    </div>
                    <div className="shrink-0 text-xs text-muted-foreground">
                      {x.category} · {x.format}
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              {tc("cancel")}
            </Button>
            <Button type="button" disabled={mutGenerate.isPending || !canGenerate} onClick={() => mutGenerate.mutate()}>
              {t("generate")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

