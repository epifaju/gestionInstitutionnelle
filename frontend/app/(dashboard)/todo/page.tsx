"use client";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { useAuthStore } from "@/lib/store";
import { todoDashboardService, type TodoActionItem, type TodoDashboardResponse } from "@/services/todo-dashboard.service";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { TodoActionGroup } from "@/components/todo/TodoActionGroup";

type Filtre = "TOUTES" | "CRITIQUES" | "URGENTES" | "NORMALES";
type Tri = "URGENCE" | "DATE" | "CATEGORIE";

function rankUrgence(n: TodoActionItem["niveauUrgence"]) {
  if (n === "CRITIQUE") return 0;
  if (n === "URGENT") return 1;
  return 2;
}

function includesByFilter(it: TodoActionItem, f: Filtre) {
  if (f === "TOUTES") return true;
  if (f === "CRITIQUES") return it.niveauUrgence === "CRITIQUE";
  if (f === "URGENTES") return it.niveauUrgence === "URGENT";
  return it.niveauUrgence === "NORMAL";
}

function sortItems(items: TodoActionItem[], tri: Tri) {
  const copy = [...items];
  if (tri === "DATE") {
    copy.sort((a, b) => (b.dateCreation ?? "").localeCompare(a.dateCreation ?? ""));
    return copy;
  }
  if (tri === "CATEGORIE") {
    copy.sort((a, b) => (a.type ?? "").localeCompare(b.type ?? "") || (a.titre ?? "").localeCompare(b.titre ?? ""));
    return copy;
  }
  copy.sort((a, b) => rankUrgence(a.niveauUrgence) - rankUrgence(b.niveauUrgence) || (b.dateCreation ?? "").localeCompare(a.dateCreation ?? ""));
  return copy;
}

function flatten(data: TodoDashboardResponse | undefined) {
  const groups = (data?.sections ?? []).flatMap((s) => s.groupes ?? []);
  return groups;
}

export default function TodoPage() {
  const user = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const [filtre, setFiltre] = useState<Filtre>("TOUTES");
  const [tri, setTri] = useState<Tri>("URGENCE");
  const [batching, setBatching] = useState(false);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["todo-dashboard"],
    queryFn: () => todoDashboardService.getTodoDashboard(),
    staleTime: 5 * 60 * 1000,
    refetchInterval: 5 * 60 * 1000,
    refetchOnWindowFocus: true,
    enabled: user?.role !== "EMPLOYE",
  });

  const groups = useMemo(() => {
    const g = flatten(data);
    return g
      .map((grp) => ({
        ...grp,
        items: sortItems(grp.items.filter((it) => includesByFilter(it, filtre)), tri),
      }))
      .filter((grp) => grp.items.length > 0 || grp.count > 0);
  }, [data, filtre, tri]);

  if (user?.role === "EMPLOYE") return null;

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-[200px]" />
        <Skeleton className="h-[200px]" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <Card className="border-border">
        <CardHeader>
          <CardTitle className="text-base">📋 À faire</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">Impossible de charger les actions.</p>
          <Button type="button" variant="outline" size="sm" onClick={() => refetch()}>
            Réessayer
          </Button>
        </CardContent>
      </Card>
    );
  }

  async function toutMarquerTraite() {
    if (user?.role !== "ADMIN") return;
    if (!confirm("Confirmer : exécuter les actions rapides (validation / approbation / marquer payé) sur les éléments affichés ?")) return;

    const actionable = groups.flatMap((g) =>
      g.items.map((it) => ({ groupeCategorie: g.categorie, item: it }))
    );
    if (actionable.length === 0) return;

    const max = 20;
    setBatching(true);
    try {
      for (const { groupeCategorie, item } of actionable.slice(0, max)) {
        const c = (groupeCategorie ?? "").toUpperCase();
        const typeAction =
          c === "CONGES_A_VALIDER"
            ? "VALIDER_CONGE"
            : c === "MISSIONS_A_APPROUVER"
              ? "APPROUVER_MISSION"
              : c === "SALAIRES_A_VERSER"
                ? "MARQUER_PAIE_PAYEE"
                : null;
        if (!typeAction) continue;
        await todoDashboardService.executerAction(item.type, item.id, { typeAction });
      }
      toast.success("Traitement batch terminé");
      queryClient.invalidateQueries({ queryKey: ["todo-dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["sidebar-comptages"] });
    } catch (e) {
      toast.error((e as Error)?.message ?? "Erreur lors du traitement batch.");
    } finally {
      setBatching(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">📋 À faire</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {data.totalActionsGlobal} action(s) dont {data.totalActionsUrgentes} urgente(s)
          </p>
        </div>
        <div className="flex items-center gap-2">
          {data.totalActionsUrgentes > 0 ? (
            <Badge className="bg-red-600 text-white hover:bg-red-600">{data.totalActionsUrgentes} urgentes</Badge>
          ) : null}
          <Button type="button" variant="outline" size="sm" onClick={() => refetch()} disabled={batching}>
            🔄 Actualiser
          </Button>
          {user?.role === "ADMIN" ? (
            <Button type="button" variant="destructive" size="sm" onClick={() => void toutMarquerTraite()} disabled={batching}>
              Tout marquer comme traité
            </Button>
          ) : null}
        </div>
      </div>

      <Card className="border-border">
        <CardContent className="flex flex-wrap items-center gap-2 p-4">
          <span className="text-sm font-medium text-foreground">Filtres</span>
          <Button type="button" size="sm" variant={filtre === "TOUTES" ? "default" : "outline"} onClick={() => setFiltre("TOUTES")}>
            Toutes
          </Button>
          <Button type="button" size="sm" variant={filtre === "CRITIQUES" ? "default" : "outline"} onClick={() => setFiltre("CRITIQUES")}>
            Critiques
          </Button>
          <Button type="button" size="sm" variant={filtre === "URGENTES" ? "default" : "outline"} onClick={() => setFiltre("URGENTES")}>
            Urgentes
          </Button>
          <Button type="button" size="sm" variant={filtre === "NORMALES" ? "default" : "outline"} onClick={() => setFiltre("NORMALES")}>
            Normales
          </Button>

          <span className="ml-auto text-sm font-medium text-foreground">Tri</span>
          <Button type="button" size="sm" variant={tri === "URGENCE" ? "default" : "outline"} onClick={() => setTri("URGENCE")}>
            Par urgence
          </Button>
          <Button type="button" size="sm" variant={tri === "DATE" ? "default" : "outline"} onClick={() => setTri("DATE")}>
            Par date
          </Button>
          <Button type="button" size="sm" variant={tri === "CATEGORIE" ? "default" : "outline"} onClick={() => setTri("CATEGORIE")}>
            Par catégorie
          </Button>
        </CardContent>
      </Card>

      <div className="space-y-4">
        {groups.length === 0 ? (
          <Card className="border-border">
            <CardContent className="p-6 text-sm text-muted-foreground">✅ Tout est à jour — aucune action à afficher.</CardContent>
          </Card>
        ) : (
          groups.map((g) => <TodoActionGroup key={g.categorie} groupe={g} onRefresh={() => refetch()} />)
        )}
      </div>
    </div>
  );
}

