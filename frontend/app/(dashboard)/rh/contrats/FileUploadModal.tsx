"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";

export function FileUploadModal({
  open,
  onOpenChange,
  title,
  description,
  accept,
  onUpload,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  title: string;
  description?: string;
  accept?: string;
  onUpload: (file: File) => Promise<void>;
}) {
  const tc = useTranslations("Common");
  const t = useTranslations("RH.contrats.upload");
  const [file, setFile] = useState<File | null>(null);
  const [pending, setPending] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) {
          setFile(null);
          setErr(null);
        }
      }}
    >
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description ? <DialogDescription>{description}</DialogDescription> : null}
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="file">{t("file")}</Label>
          <input
            id="file"
            type="file"
            accept={accept}
            className="block w-full text-sm"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          {err ? <p className="text-sm text-red-600">{err}</p> : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
            {tc("close")}
          </Button>
          <Button
            type="button"
            disabled={!file || pending}
            onClick={async () => {
              if (!file) return;
              setPending(true);
              setErr(null);
              try {
                await onUpload(file);
                onOpenChange(false);
                setFile(null);
              } catch (e: unknown) {
                setErr(e instanceof Error ? e.message : tc("errorGeneric"));
              } finally {
                setPending(false);
              }
            }}
          >
            {pending ? tc("loading") : tc("save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
