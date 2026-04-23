"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { SalarieForm } from "@/components/forms/SalarieForm";
import type { SalarieRequest } from "@/lib/types/rh";

export function SalarieModal({
  open,
  onClose,
  onCreate,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (body: SalarieRequest) => Promise<void>;
}) {
  const ts = useTranslations("RH.salaries");
  const tc = useTranslations("Common");
  const [err, setErr] = useState<string | null>(null);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-3xl overflow-hidden rounded-lg bg-white p-4 shadow-xl">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">{ts("modalTitleNew")}</h2>
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            {tc("close")}
          </Button>
        </div>
        {err && <p className="mb-2 text-sm text-red-600">{err}</p>}
        <SalarieForm
          submitLabel={ts("modalCreate")}
          onSubmit={async (data) => {
            setErr(null);
            try {
              await onCreate(data);
              onClose();
            } catch (e: unknown) {
              const msg =
                e && typeof e === "object" && "message" in e
                  ? String((e as { message: unknown }).message)
                  : tc("errorGeneric");
              setErr(msg);
            }
          }}
        />
      </div>
    </div>
  );
}
