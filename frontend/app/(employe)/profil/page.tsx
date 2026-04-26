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
      <div className="rounded-2xl border border-border bg-card p-4 text-card-foreground">
        <h1 className="text-lg font-semibold text-foreground">Mon profil</h1>
        <p className="text-sm text-muted-foreground">Informations liées à votre dossier salarié.</p>
      </div>

      <div className="rounded-2xl border border-border bg-card p-4 text-card-foreground">
        {q.isLoading ? (
          <p className="text-sm text-muted-foreground">Chargement…</p>
        ) : q.data ? (
          <div className="space-y-2 text-sm">
            <div className="flex justify-between gap-3">
              <span className="text-muted-foreground">Matricule</span>
              <span className="font-medium text-foreground">{q.data.matricule}</span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-muted-foreground">Nom</span>
              <span className="font-medium text-foreground">
                {q.data.prenom} {q.data.nom}
              </span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-muted-foreground">Service</span>
              <span className="font-medium text-foreground">{q.data.service ?? "—"}</span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-muted-foreground">Poste</span>
              <span className="font-medium text-foreground">{q.data.poste ?? "—"}</span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-muted-foreground">Email</span>
              <span className="font-medium text-foreground">{q.data.email ?? "—"}</span>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">Profil indisponible.</p>
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

