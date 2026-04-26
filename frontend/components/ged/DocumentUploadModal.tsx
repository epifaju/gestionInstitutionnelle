"use client";

import { useEffect, useMemo, useState } from "react";
import { useDropzone } from "react-dropzone";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { DocumentUploadRequest } from "@/lib/types/ged";
import { useTranslations } from "next-intl";

const TYPES = [
  "CONTRAT",
  "FACTURE_JUS",
  "ORDRE_MISSION",
  "RAPPORT",
  "CIRCULAIRE",
  "NOTE_DIPLOMATIQUE",
  "ACREDITATION",
  "VISA",
  "PASSEPORT",
  "AUTRE",
];

export function DocumentUploadModal({
  open,
  onClose,
  initialFile,
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  initialFile?: File | null;
  onSubmit: (req: DocumentUploadRequest, file: File) => Promise<void>;
}) {
  const t = useTranslations("Documents.uploadModal");
  const tc = useTranslations("Common");
  const [file, setFile] = useState<File | null>(initialFile ?? null);
  const [titre, setTitre] = useState("");
  const [description, setDescription] = useState("");
  const [typeDocument, setTypeDocument] = useState("AUTRE");
  const [tagsRaw, setTagsRaw] = useState("");
  const [visibilite, setVisibilite] = useState("ORGANISATION");
  const [serviceCible, setServiceCible] = useState("");
  const [dateExpiration, setDateExpiration] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setFile(initialFile ?? null);
    setTitre("");
    setDescription("");
    setTypeDocument("AUTRE");
    setTagsRaw("");
    setVisibilite("ORGANISATION");
    setServiceCible("");
    setDateExpiration("");
  }, [open, initialFile]);

  const tags = useMemo(() => {
    const parts = tagsRaw
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean);
    return parts.length ? parts : null;
  }, [tagsRaw]);

  const dz = useDropzone({
    multiple: false,
    onDrop: (accepted) => {
      if (accepted?.[0]) setFile(accepted[0]);
    },
  });

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-lg bg-card p-4 text-card-foreground shadow-xl">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">{t("title")}</h2>
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            {tc("close")}
          </Button>
        </div>

        <div
          {...dz.getRootProps()}
          className="mb-4 cursor-pointer rounded-lg border-2 border-dashed border-border bg-muted p-4 text-center"
        >
          <input {...dz.getInputProps()} />
          <p className="text-sm text-foreground">{file ? file.name : t("dropzoneText")}</p>
          <p className="mt-1 text-xs text-muted-foreground">{t("dropzoneHint")}</p>
        </div>

        <form
          className="space-y-3"
          onSubmit={async (e) => {
            e.preventDefault();
            if (!file) return;
            setIsSubmitting(true);
            try {
              const req: DocumentUploadRequest = {
                titre: titre.trim() || (file.name ?? "Document"),
                description: description.trim() || null,
                typeDocument,
                tags,
                visibilite,
                serviceCible: visibilite === "SERVICE" ? serviceCible.trim() || null : null,
                dateExpiration: dateExpiration || null,
              };
              await onSubmit(req, file);
              onClose();
            } finally {
              setIsSubmitting(false);
            }
          }}
        >
          <div>
            <Label>{t("fieldTitle")}</Label>
            <Input value={titre} onChange={(e) => setTitre(e.target.value)} placeholder={t("fieldTitlePlaceholder")} />
          </div>
          <div>
            <Label>{t("fieldDescription")}</Label>
            <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder={t("fieldDescriptionPlaceholder")} />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label>{t("fieldType")}</Label>
              <select
                className="flex h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground"
                value={typeDocument}
                onChange={(e) => setTypeDocument(e.target.value)}
              >
                {TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label>{t("fieldVisibility")}</Label>
              <select
                className="flex h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground"
                value={visibilite}
                onChange={(e) => setVisibilite(e.target.value)}
              >
                <option value="PRIVE">PRIVE</option>
                <option value="SERVICE">SERVICE</option>
                <option value="ORGANISATION">ORGANISATION</option>
                <option value="PUBLIC">PUBLIC</option>
              </select>
            </div>
          </div>
          {visibilite === "SERVICE" ? (
            <div>
              <Label>{t("fieldTargetService")}</Label>
              <Input value={serviceCible} onChange={(e) => setServiceCible(e.target.value)} placeholder={t("fieldTargetServicePlaceholder")} />
            </div>
          ) : null}
          <div>
            <Label>{t("fieldTags")}</Label>
            <Input value={tagsRaw} onChange={(e) => setTagsRaw(e.target.value)} placeholder={t("fieldTagsPlaceholder")} />
          </div>
          <div>
            <Label>{t("fieldExpirationDate")}</Label>
            <Input type="date" value={dateExpiration} onChange={(e) => setDateExpiration(e.target.value)} />
          </div>

          <Button type="submit" disabled={!file || isSubmitting}>
            {t("submit")}
          </Button>
        </form>
      </div>
    </div>
  );
}

