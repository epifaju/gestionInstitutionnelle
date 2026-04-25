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
      <div className="rounded-2xl bg-white p-4 shadow-sm border border-slate-200">
        <p className="text-sm text-slate-600">{todayLabel()}</p>
        <p className="mt-1 text-lg font-semibold text-slate-900">Bonjour {user?.prenom ?? ""}</p>
      </div>

      <div className="grid gap-3">
        <div className="rounded-2xl bg-white p-4 shadow-sm border border-slate-200">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Solde congés</p>
          <p className="mt-2 text-4xl font-bold text-slate-900">{restants ?? "—"}</p>
          <p className="mt-1 text-sm text-slate-600">
            Droit: {droit ?? "—"} · Pris: {pris ?? "—"}
          </p>
          <div className="mt-3 h-2 rounded-full bg-slate-100">
            <div
              className="h-2 rounded-full bg-indigo-600"
              style={{
                width: droit ? `${Math.max(0, Math.min(100, ((Number(pris ?? 0) / Number(droit)) * 100)))}%` : "0%",
              }}
            />
          </div>
        </div>

        <div className="rounded-2xl bg-white p-4 shadow-sm border border-slate-200">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Dernière paie</p>
          <p className="mt-2 text-lg font-semibold text-slate-900">{lastPaie ? `${lastPaie.mois}/${lastPaie.annee}` : "—"}</p>
          <p className="text-sm text-slate-600">{lastPaie ? `${lastPaie.montant} ${lastPaie.devise}` : ""}</p>
          <p className="mt-1 text-xs text-slate-500">{lastPaie ? lastPaie.statut : ""}</p>
        </div>

        <div className="rounded-2xl bg-white p-4 shadow-sm border border-slate-200">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Prochains congés validés</p>
          {upcoming.length === 0 ? (
            <p className="mt-2 text-sm text-slate-600">Aucun congé validé à venir.</p>
          ) : (
            <div className="mt-2 space-y-2">
              {upcoming.map((c) => (
                <div key={c.id} className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-sm">
                  <div className="font-medium text-slate-900">{c.typeConge}</div>
                  <div className="text-slate-600">
                    {c.dateDebut} → {c.dateFin}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-2xl bg-white p-4 shadow-sm border border-slate-200">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Notifications</p>
          {notifs.length === 0 ? (
            <p className="mt-2 text-sm text-slate-600">Aucune notification non lue.</p>
          ) : (
            <div className="mt-2 space-y-2">
              {notifs.map((n) => (
                <div key={n.id} className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
                  <div className="text-sm font-medium text-slate-900">{n.titre}</div>
                  <div className="text-xs text-slate-600 line-clamp-2">{n.message}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

