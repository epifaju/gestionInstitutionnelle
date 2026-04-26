"use client";

import { useQuery } from "@tanstack/react-query";

import { listEmployeDocuments } from "@/services/employe.service";
import { Button } from "@/components/ui/button";

export default function EmployeMesDocumentsPage() {
  const q = useQuery({ queryKey: ["employe", "documents"], queryFn: () => listEmployeDocuments() });
  const docs = q.data ?? [];

  return (
    <div className="mx-auto max-w-md space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-900">Mes documents</h1>
        <Button type="button" variant="outline" size="sm" onClick={() => q.refetch()}>
          Rafraîchir
        </Button>
      </div>

      {q.isLoading ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-600">Chargement…</div>
      ) : docs.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-600">Aucun document.</div>
      ) : (
        <div className="grid grid-cols-2 gap-2">
          {docs.map((d) => (
            <a
              key={d.nomFichier}
              href={d.url}
              target="_blank"
              rel="noreferrer"
              className="rounded-2xl border border-slate-200 bg-white p-3 text-sm text-slate-700 active:scale-[0.99]"
            >
              <div className="text-xs text-slate-500">PDF</div>
              <div className="mt-1 line-clamp-2 font-medium">{d.nomFichier}</div>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

