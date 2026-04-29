"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { GenerateDocumentDialog } from "@/components/templates/GenerateDocumentDialog";
import { useAuthStore } from "@/lib/store";
import { getPresignedUrl } from "@/services/documents.service";
import type { GeneratedDocumentDto } from "@/services/template.service";
import { listGeneratedDocuments } from "@/services/template.service";

export default function CourriersPage() {
  const t = useTranslations("Courriers");
  const tc = useTranslations("Common");
  const orgId = useAuthStore((s) => s.user?.organisationId) ?? "";

  const [query, setQuery] = useState("");

  const { data, isLoading, refetch } = useQuery({
    queryKey: ["templates", "generated", "COURRIER", orgId],
    queryFn: () => listGeneratedDocuments("COURRIER", orgId),
    enabled: !!orgId,
  });

  const rows = useMemo(() => {
    const arr = (data ?? []) as GeneratedDocumentDto[];
    const q = query.trim().toLowerCase();
    if (!q) return arr;
    return arr.filter((x) => (x.outputFormat ?? "").toLowerCase().includes(q) || (x.id ?? "").toLowerCase().includes(q));
  }, [data, query]);

  async function openDoc(docId: string | null) {
    if (!docId) return;
    try {
      const { url } = await getPresignedUrl(docId);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch {
      toast.error(tc("errorGeneric"));
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {orgId ? <GenerateDocumentDialog subjectType="COURRIER" subjectId={orgId} /> : null}
          <Button type="button" variant="outline" size="sm" onClick={() => refetch()}>
            {t("refresh")}
          </Button>
        </div>
      </div>

      <Card className="p-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Input className="max-w-md" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t("searchPh")} />
        </div>

        {isLoading ? (
          <div className="text-sm text-muted-foreground">{tc("loading")}</div>
        ) : rows.length === 0 ? (
          <div className="text-sm text-muted-foreground">{t("empty")}</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("thCreatedAt")}</TableHead>
                <TableHead>{t("thFormat")}</TableHead>
                <TableHead>{t("thId")}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="text-sm text-muted-foreground">{r.createdAt?.slice(0, 19).replace("T", " ") ?? tc("emDash")}</TableCell>
                  <TableCell className="text-sm">{r.outputFormat ?? tc("emDash")}</TableCell>
                  <TableCell className="text-xs text-muted-foreground font-mono">{r.id}</TableCell>
                  <TableCell className="text-right">
                    <Button type="button" size="sm" variant="outline" disabled={!r.outputDocumentId} onClick={() => openDoc(r.outputDocumentId)}>
                      {tc("open")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}

