"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

import { getEmployeDroitsConges, listEmployeNotifications, listEmployePaie, listEmployeConges } from "@/services/employe.service";
import { useAuthStore } from "@/lib/store";

function todayLabel() {
  return new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "2-digit", month: "long", year: "numeric" }).format(new Date());
}

export default function EmployeHomePage() {
  const user = useAuthStore((s) => s.user);
  const annee = useMemo(() => new Date().getFullYear(), []);

  const droitsQ = useQuery({ queryKey: ["employe", "droits"], queryFn: () => getEmployeDroitsConges() });
  const paieQ = useQuery({ queryKey: ["employe", "paie", annee], queryFn: () => listEmployePaie(annee, { page: 0, size: 12 }) });
  const congesQ = useQuery({ queryKey: ["employe", "conges"], queryFn: () => listEmployeConges({ page: 0, size: 10 }) });
  const notifQ = useQuery({
    queryKey: ["employe", "notifications"],
    queryFn: () => listEmployeNotifications({ nonLuesSeulement: true, page: 0, size: 3 }),
  });

  const restants = droitsQ.data?.joursRestants ?? null;
  const droit = droitsQ.data?.joursDroit ?? null;
  const pris = droitsQ.data?.joursPris ?? null;

  const lastPaie = (paieQ.data?.content ?? []).slice().reverse().find((p) => !!p) ?? null;
  const upcoming = (congesQ.data?.content ?? []).filter((c) => c.statut === "VALIDE").slice(0, 2);
  const notifs = notifQ.data?.content ?? [];

  return (
    <div className="mx-auto max-w-md space-y-4">
      <div className="rounded-2xl border border-border bg-card p-4 text-card-foreground shadow-sm">
        <p className="text-sm text-muted-foreground">{todayLabel()}</p>
        <p className="mt-1 text-lg font-semibold text-foreground">Bonjour {user?.prenom ?? ""}</p>
      </div>

      <div className="grid gap-3">
        <div className="rounded-2xl border border-border bg-card p-4 text-card-foreground shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Solde congés</p>
          <p className="mt-2 text-4xl font-bold text-foreground">{restants ?? "—"}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Droit: {droit ?? "—"} · Pris: {pris ?? "—"}
          </p>
          <div className="mt-3 h-2 rounded-full bg-muted">
            <div
              className="h-2 rounded-full bg-indigo-600"
              style={{
                width: droit ? `${Math.max(0, Math.min(100, ((Number(pris ?? 0) / Number(droit)) * 100)))}%` : "0%",
              }}
            />
          </div>
        </div>

        <div className="rounded-2xl border border-border bg-card p-4 text-card-foreground shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Dernière paie</p>
          <p className="mt-2 text-lg font-semibold text-foreground">{lastPaie ? `${lastPaie.mois}/${lastPaie.annee}` : "—"}</p>
          <p className="text-sm text-muted-foreground">{lastPaie ? `${lastPaie.montant} ${lastPaie.devise}` : ""}</p>
          <p className="mt-1 text-xs text-muted-foreground">{lastPaie ? lastPaie.statut : ""}</p>
        </div>

        <div className="rounded-2xl border border-border bg-card p-4 text-card-foreground shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Prochains congés validés</p>
          {upcoming.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">Aucun congé validé à venir.</p>
          ) : (
            <div className="mt-2 space-y-2">
              {upcoming.map((c) => (
                <div key={c.id} className="rounded-lg border border-border bg-muted px-3 py-2 text-sm">
                  <div className="font-medium text-foreground">{c.typeConge}</div>
                  <div className="text-muted-foreground">
                    {c.dateDebut} → {c.dateFin}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-2xl border border-border bg-card p-4 text-card-foreground shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Notifications</p>
          {notifs.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">Aucune notification non lue.</p>
          ) : (
            <div className="mt-2 space-y-2">
              {notifs.map((n) => (
                <div key={n.id} className="rounded-lg border border-border bg-muted px-3 py-2">
                  <div className="text-sm font-medium text-foreground">{n.titre}</div>
                  <div className="text-xs text-muted-foreground line-clamp-2">{n.message}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

