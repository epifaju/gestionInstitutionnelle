"use client";

import { useQuery } from "@tanstack/react-query";

import { getEmployeProfil } from "@/services/employe.service";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/lib/store";

export default function EmployeProfilPage() {
  const logout = useAuthStore((s) => s.logout);
  const q = useQuery({ queryKey: ["employe", "profil"], queryFn: () => getEmployeProfil() });

  return (
    <div className="mx-auto max-w-md space-y-4">
      <div className="rounded-2xl border border-slate-200 bg-white p-4">
        <h1 className="text-lg font-semibold text-slate-900">Mon profil</h1>
        <p className="text-sm text-slate-600">Informations liées à votre dossier salarié.</p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-4">
        {q.isLoading ? (
          <p className="text-sm text-slate-600">Chargement…</p>
        ) : q.data ? (
          <div className="space-y-2 text-sm">
            <div className="flex justify-between gap-3">
              <span className="text-slate-500">Matricule</span>
              <span className="font-medium text-slate-900">{q.data.matricule}</span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-slate-500">Nom</span>
              <span className="font-medium text-slate-900">
                {q.data.prenom} {q.data.nom}
              </span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-slate-500">Service</span>
              <span className="font-medium text-slate-900">{q.data.service ?? "—"}</span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-slate-500">Poste</span>
              <span className="font-medium text-slate-900">{q.data.poste ?? "—"}</span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-slate-500">Email</span>
              <span className="font-medium text-slate-900">{q.data.email ?? "—"}</span>
            </div>
          </div>
        ) : (
          <p className="text-sm text-slate-600">Profil indisponible.</p>
        )}
      </div>

      <Button
        type="button"
        variant="destructive"
        className="w-full"
        onClick={() => {
          logout();
          window.location.href = "/login";
        }}
      >
        Se déconnecter
      </Button>
    </div>
  );
}

