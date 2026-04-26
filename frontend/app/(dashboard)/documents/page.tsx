"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DataTable } from "@/components/tables/DataTable";
import type { ColumnDef } from "@tanstack/react-table";
import type { DocumentResponse } from "@/lib/types/ged";
import { searchDocuments, uploadDocument, deleteDocument, getPresignedUrl } from "@/services/documents.service";
import { DocumentCard } from "@/components/ged/DocumentCard";
import { DocumentUploadModal } from "@/components/ged/DocumentUploadModal";
import { useDropzone } from "react-dropzone";
import { LayoutGrid, List } from "lucide-react";
import type { DocumentUploadRequest } from "@/lib/types/ged";
import { useTranslations } from "next-intl";

function fmtSize(bytes: number) {
  if (!Number.isFinite(bytes)) return "—";
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(0)} Ko`;
  return `${(kb / 1024).toFixed(1)} Mo`;
}

export default function DocumentsPage() {
  const t = useTranslations("Documents");
  const tc = useTranslations("Common");
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [type, setType] = useState("");
  const [tags, setTags] = useState("");
  const [service, setService] = useState("");
  const [expSoon, setExpSoon] = useState(false);
  const [view, setView] = useState<"grid" | "list">("grid");
  const [uploadOpen, setUploadOpen] = useState(false);
  const [initialFile, setInitialFile] = useState<File | null>(null);

  const tagsArr = useMemo(
    () =>
      tags
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean),
    [tags]
  );

  const { data, isLoading } = useQuery({
    queryKey: ["documents", page, query, type, tagsArr.join("|"), service, expSoon],
    queryFn: () =>
      searchDocuments({
        page,
        size: 20,
        query: query || undefined,
        type: type || undefined,
        tags: tagsArr.length ? tagsArr : undefined,
        service: service || undefined,
        expirantBientot: expSoon || undefined,
      }),
  });

  const rows = data?.content ?? [];

  const mutUpload = useMutation({
    mutationFn: ({ req, file }: { req: DocumentUploadRequest; file: File }) => uploadDocument(req, file),
    onSuccess: () => {
      toast.success(t("toastUploaded"));
      qc.invalidateQueries({ queryKey: ["documents"] });
    },
  });

  const mutDelete = useMutation({
    mutationFn: (id: string) => deleteDocument(id),
    onSuccess: () => {
      toast.success(t("toastDeleted"));
      qc.invalidateQueries({ queryKey: ["documents"] });
    },
  });

  const dz = useDropzone({
    noClick: true,
    multiple: false,
    onDrop: (accepted) => {
      const f = accepted?.[0];
      if (!f) return;
      setInitialFile(f);
      setUploadOpen(true);
    },
  });

  const columns = useMemo<ColumnDef<DocumentResponse>[]>(
    () => [
      { accessorKey: "titre", header: t("thTitle") },
      { accessorKey: "typeDocument", header: t("thType") },
      {
        id: "tags",
        header: t("thTags"),
        cell: ({ row }) => (row.original.tags ?? []).slice(0, 4).join(", ") || tc("emDash"),
      },
      {
        id: "taille",
        header: t("thSize"),
        cell: ({ row }) => <span className="tabular-nums">{fmtSize(row.original.tailleOctets)}</span>,
      },
      { accessorKey: "uploadeParNomComplet", header: t("thUploadedBy"), cell: ({ row }) => row.original.uploadeParNomComplet ?? tc("emDash") },
      { accessorKey: "createdAt", header: t("thDate"), cell: ({ row }) => (row.original.createdAt ? row.original.createdAt.slice(0, 10) : tc("emDash")) },
      { accessorKey: "dateExpiration", header: t("thExpiration"), cell: ({ row }) => row.original.dateExpiration ?? tc("emDash") },
    ],
    [t, tc]
  );

  return (
    <div {...dz.getRootProps()} className="space-y-4">
      <input {...dz.getInputProps()} />

      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <div className="flex gap-2">
          <Button type="button" variant="outline" onClick={() => setView((v) => (v === "grid" ? "list" : "grid"))}>
            {view === "grid" ? <List className="mr-2 h-4 w-4" /> : <LayoutGrid className="mr-2 h-4 w-4" />}
            {view === "grid" ? t("viewList") : t("viewGrid")}
          </Button>
          <Button
            type="button"
            onClick={() => {
              setInitialFile(null);
              setUploadOpen(true);
            }}
          >
            + {t("upload")}
          </Button>
        </div>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <aside className="space-y-3 rounded-lg border border-border bg-card p-3 text-card-foreground">
          <p className="text-xs font-semibold uppercase text-muted-foreground">{t("filters")}</p>
          <div>
            <label className="text-xs text-muted-foreground">{t("type")}</label>
            <Input className="h-8 text-sm" value={type} onChange={(e) => setType(e.target.value)} placeholder={t("typePlaceholder")} />
          </div>
          <div>
            <label className="text-xs text-muted-foreground">{t("tags")}</label>
            <Input className="h-8 text-sm" value={tags} onChange={(e) => setTags(e.target.value)} placeholder={t("tagsPlaceholder")} />
          </div>
          <div>
            <label className="text-xs text-muted-foreground">{t("service")}</label>
            <Input className="h-8 text-sm" value={service} onChange={(e) => setService(e.target.value)} placeholder={t("servicePlaceholder")} />
          </div>
          <label className="flex items-center gap-2 text-sm text-foreground">
            <input type="checkbox" checked={expSoon} onChange={(e) => setExpSoon(e.target.checked)} />
            {t("expiringSoon")}
          </label>
          <p className="text-xs text-muted-foreground">{t("hintDropzone")}</p>
        </aside>

        <div className="md:col-span-3">
          {view === "list" ? (
            <DataTable
              columns={columns}
              data={rows}
              isLoading={isLoading}
              pagination={
                data
                  ? { page: data.page, size: data.size, totalElements: data.totalElements, totalPages: data.totalPages }
                  : undefined
              }
              onPageChange={(p) => setPage(p)}
              onSearch={(q) => setQuery(q)}
              searchPlaceholder={tc("searchPlaceholder")}
            />
          ) : (
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
              {isLoading ? (
                <p className="text-sm text-muted-foreground">{tc("loading")}</p>
              ) : rows.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("empty")}</p>
              ) : (
                rows.map((d) => (
                  <DocumentCard
                    key={d.id}
                    doc={d}
                    onDownload={async () => {
                      const res = await getPresignedUrl(d.id);
                      window.open(res.url, "_blank", "noopener,noreferrer");
                    }}
                    onDelete={() => mutDelete.mutate(d.id)}
                  />
                ))
              )}
            </div>
          )}
        </div>
      </div>

      <DocumentUploadModal
        open={uploadOpen}
        initialFile={initialFile}
        onClose={() => setUploadOpen(false)}
        onSubmit={async (req, file) => {
          await mutUpload.mutateAsync({ req, file });
        }}
      />
    </div>
  );
}

