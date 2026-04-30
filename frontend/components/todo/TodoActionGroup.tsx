"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { toast } from "sonner";

import type { TodoActionGroup as TodoActionGroupT, TodoActionItem } from "@/services/todo-dashboard.service";
import { todoDashboardService } from "@/services/todo-dashboard.service";
import { TodoActionCard, type ActionDisponible } from "@/components/todo/TodoActionCard";

function actionsForCategorie(categorie: string): ActionDisponible[] {
  const c = (categorie ?? "").toUpperCase();
  if (c === "CONGES_A_VALIDER") {
    return [
      { typeAction: "VALIDER_CONGE", label: "✓ Valider", variante: "success" },
      { typeAction: "REJETER_CONGE", label: "✗ Rejeter", variante: "danger", requiresComment: true },
    ];
  }
  if (c === "SALAIRES_A_VERSER") {
    return [{ typeAction: "MARQUER_PAIE_PAYEE", label: "✓ Marquer payé", variante: "success" }];
  }
  if (c === "MISSIONS_A_APPROUVER") {
    return [
      { typeAction: "APPROUVER_MISSION", label: "✓ Approuver", variante: "success" },
      { typeAction: "REJETER_MISSION", label: "✗ Refuser", variante: "danger", requiresComment: true },
    ];
  }
  return [];
}

export function TodoActionGroup({ groupe, onRefresh }: { groupe: TodoActionGroupT; onRefresh: () => void }) {
  const [loadingItemId, setLoadingItemId] = useState<string | null>(null);
  const actionsDisponibles = useMemo(() => actionsForCategorie(groupe.categorie), [groupe.categorie]);

  async function onActionForItem(item: TodoActionItem, typeAction: string, commentaire?: string) {
    setLoadingItemId(item.id);
    try {
      const res = await todoDashboardService.executerAction(item.type, item.id, { typeAction, commentaire });
      if (res.succes) {
        toast.success(res.message || "Action effectuée");
        onRefresh();
      } else {
        toast.error(res.message || "Action impossible");
      }
    } catch (e) {
      toast.error((e as Error)?.message ?? "Action impossible");
    } finally {
      setLoadingItemId(null);
    }
  }

  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-foreground">
            {groupe.icone} {groupe.label}
          </p>
          <p className="text-xs text-muted-foreground">{groupe.count} action(s)</p>
        </div>
        <Link href={groupe.lienVoirTout} className="text-sm font-medium text-indigo-700 hover:underline">
          Voir tout →
        </Link>
      </div>

      <div className="mt-3 space-y-2">
        {groupe.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">Aucun élément à afficher.</p>
        ) : (
          groupe.items.slice(0, 5).map((it) => (
            <TodoActionCard
              key={it.id}
              item={it}
              actionsDisponibles={actionsDisponibles}
              isLoading={loadingItemId === it.id}
              onAction={actionsDisponibles.length > 0 ? (typeAction, commentaire) => onActionForItem(it, typeAction, commentaire) : undefined}
            />
          ))
        )}

        {groupe.count > 5 ? (
          <div className="pt-1 text-sm text-muted-foreground">
            + {groupe.count - 5} autres →{" "}
            <Link href={groupe.lienVoirTout} className="font-medium text-indigo-700 hover:underline">
              {groupe.lienVoirTout}
            </Link>
          </div>
        ) : null}
      </div>
    </div>
  );
}

