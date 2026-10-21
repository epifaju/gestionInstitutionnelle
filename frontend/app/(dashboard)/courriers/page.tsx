"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { CourrierModal } from "@/components/courriers/CourrierModal";
import { GenerateDocumentDialog } from "@/components/templates/GenerateDocumentDialog";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuthStore } from "@/lib/store";
import type { CourrierRequest, CourrierResponse } from "@/lib/types/courriers";
import {
  createCourrier,
  listCourriers,
  rattacherDocumentCourrier,
  updateCourrier,
} from "@/services/courriers.service";
import { getPresignedUrl, uploadDocument } from "@/services/documents.service";
import type { GeneratedDocumentDto } from "@/services/template.service";
import { listGeneratedDocuments } from "@/services/template.service";

export default function CourriersPage() {
  const t = useTranslations("Courriers");
  const tc = useTranslations("Common");
  const orgId = useAuthStore((s) => s.user?.organisationId) ?? "";
  const role = useAuthStore((s) => s.user?.role);
  const canWrite = role === "ADMIN" || role === "RH";
  const qc = useQueryClient();

  const [tab, setTab] = useState("registre");
  const [query, setQuery] = useState("");
  const [sensFilter, setSensFilter] = useState("");
  const [statutFilter, setStatutFilter] = useState("");
  const [genQuery, setGenQuery] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CourrierResponse | null>(null);

  const { data: registrePage, isLoading: registreLoading, refetch: refetchRegistre } = useQuery({
    queryKey: ["courriers", "registre", orgId, sensFilter, statutFilter, query],
    queryFn: () =>
      listCourriers({
        page: 0,
        size: 50,
        sens: sensFilter || undefined,
        statut: statutFilter || undefined,
        q: query.trim() || undefined,
      }),
    enabled: !!orgId && tab === "registre",
  });

  const { data: generated, isLoading: genLoading, refetch: refetchGen } = useQuery({
    queryKey: ["templates", "generated", "COURRIER", orgId],
    queryFn: () => listGeneratedDocuments("COURRIER", orgId),
    enabled: !!orgId && tab === "generation",
  });

  const rows = registrePage?.content ?? [];

  const genRows = useMemo(() => {
    const arr = (generated ?? []) as GeneratedDocumentDto[];
    const q = genQuery.trim().toLowerCase();
    if (!q) return arr;
    return arr.filter(
      (x) => (x.outputFormat ?? "").toLowerCase().includes(q) || (x.id ?? "").toLowerCase().includes(q)
    );
  }, [generated, genQuery]);

  const saveMutation = useMutation({
    mutationFn: async ({ data, file, id }: { data: CourrierRequest; file: File | null; id?: string }) => {
      const saved = id ? await updateCourrier(id, data) : await createCourrier(data);
      if (file) {
        const doc = await uploadDocument(
          {
            titre: data.objet,
            typeDocument: "COURRIER",
            visibilite: "ORGANISATION",
            entiteLieeType: "COURRIER_REGISTRE",
            entiteLieeId: saved.id,
          },
          file
        );
        return rattacherDocumentCourrier(saved.id, doc.id);
      }
      return saved;
    },
    onSuccess: () => {
      toast.success(t("toast.saved"));
      qc.invalidateQueries({ queryKey: ["courriers", "registre"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  async function openDoc(docId: string | null) {
    if (!docId) return;
    try {
      const { url } = await getPresignedUrl(docId);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch {
      toast.error(tc("errorGeneric"));
    }
  }

  function openCreate() {
    setEditing(null);
    setModalOpen(true);
  }

  function openEdit(row: CourrierResponse) {
    setEditing(row);
    setModalOpen(true);
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
      </div>

      <Tabs value={tab} onValueChange={setTab} defaultValue="registre">
        <TabsList>
          <TabsTrigger value="registre">{t("tabs.registre")}</TabsTrigger>
          <TabsTrigger value="generation">{t("tabs.generation")}</TabsTrigger>
        </TabsList>

        <TabsContent value="registre">
          <Card className="space-y-3 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <Input
                  className="max-w-xs"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder={t("searchPh")}
                />
                <select
                  className="flex h-9 rounded-md border border-input bg-transparent px-2 text-sm"
                  value={sensFilter}
                  onChange={(e) => setSensFilter(e.target.value)}
                >
                  <option value="">{t("filters.allSens")}</option>
                  <option value="ENTREE">{t("sens.ENTREE")}</option>
                  <option value="SORTIE">{t("sens.SORTIE")}</option>
                </select>
                <select
                  className="flex h-9 rounded-md border border-input bg-transparent px-2 text-sm"
                  value={statutFilter}
                  onChange={(e) => setStatutFilter(e.target.value)}
                >
                  <option value="">{t("filters.allStatut")}</option>
                  {(
                    ["BROUILLON", "ENREGISTRE", "ENVOYE", "RECU", "CLASSE", "ANNULE"] as const
                  ).map((s) => (
                    <option key={s} value={s}>
                      {t(`statut.${s}`)}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-wrap gap-2">
                {canWrite ? (
                  <Button type="button" size="sm" onClick={openCreate}>
                    {t("create")}
                  </Button>
                ) : null}
                <Button type="button" variant="outline" size="sm" onClick={() => refetchRegistre()}>
                  {t("refresh")}
                </Button>
              </div>
            </div>

            {registreLoading ? (
              <div className="text-sm text-muted-foreground">{tc("loading")}</div>
            ) : rows.length === 0 ? (
              <div className="text-sm text-muted-foreground">{t("emptyRegistre")}</div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("thDate")}</TableHead>
                    <TableHead>{t("thSens")}</TableHead>
                    <TableHead>{t("thObjet")}</TableHead>
                    <TableHead>{t("thReference")}</TableHead>
                    <TableHead>{t("thStatut")}</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((r) => (
                    <TableRow key={r.id}>
                      <TableCell className="text-sm text-muted-foreground">
                        {r.dateCourrier?.slice(0, 10) ?? tc("emDash")}
                      </TableCell>
                      <TableCell className="text-sm">{t(`sens.${r.sens}`)}</TableCell>
                      <TableCell className="text-sm">{r.objet}</TableCell>
                      <TableCell className="text-xs font-mono text-muted-foreground">
                        {r.reference ?? tc("emDash")}
                      </TableCell>
                      <TableCell className="text-sm">{t(`statut.${r.statut}`)}</TableCell>
                      <TableCell className="space-x-2 text-right">
                        {r.documentId ? (
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => openDoc(r.documentId)}
                          >
                            {tc("open")}
                          </Button>
                        ) : null}
                        <GenerateDocumentDialog
                          subjectType="COURRIER"
                          subjectId={r.id}
                          triggerLabel={t("generate")}
                          onGenerated={() => {
                            qc.invalidateQueries({ queryKey: ["templates", "generated", "COURRIER"] });
                          }}
                        />
                        <Button type="button" size="sm" variant="outline" onClick={() => openEdit(r)}>
                          {canWrite ? tc("modify") : tc("open")}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </TabsContent>

        <TabsContent value="generation">
          <Card className="space-y-3 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <Input
                className="max-w-md"
                value={genQuery}
                onChange={(e) => setGenQuery(e.target.value)}
                placeholder={t("searchPh")}
              />
              <div className="flex flex-wrap gap-2">
                {orgId ? (
                  <GenerateDocumentDialog
                    subjectType="COURRIER"
                    subjectId={orgId}
                    onGenerated={() => {
                      qc.invalidateQueries({ queryKey: ["templates", "generated", "COURRIER"] });
                    }}
                  />
                ) : null}
                <Button type="button" variant="outline" size="sm" onClick={() => refetchGen()}>
                  {t("refresh")}
                </Button>
              </div>
            </div>

            {genLoading ? (
              <div className="text-sm text-muted-foreground">{tc("loading")}</div>
            ) : genRows.length === 0 ? (
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
                  {genRows.map((r) => (
                    <TableRow key={r.id}>
                      <TableCell className="text-sm text-muted-foreground">
                        {r.createdAt?.slice(0, 19).replace("T", " ") ?? tc("emDash")}
                      </TableCell>
                      <TableCell className="text-sm">{r.outputFormat ?? tc("emDash")}</TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">{r.id}</TableCell>
                      <TableCell className="text-right">
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={!r.outputDocumentId}
                          onClick={() => openDoc(r.outputDocumentId)}
                        >
                          {tc("open")}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </TabsContent>
      </Tabs>

      <CourrierModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        initial={editing}
        canWrite={canWrite}
        onSubmit={async (data, file) => {
          await saveMutation.mutateAsync({ data, file, id: editing?.id });
        }}
      />
    </div>
  );
}
