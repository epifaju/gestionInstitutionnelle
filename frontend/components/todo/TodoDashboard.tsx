"use client";

import { useCallback, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";

import { todoDashboardService } from "@/services/todo-dashboard.service";
import { useAuthStore } from "@/lib/store";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { TodoSection } from "@/components/todo/TodoSection";

function relativeFromIso(iso: string) {
  const d = iso ? new Date(iso) : null;
  if (!d || Number.isNaN(d.getTime())) return "";
  const ms = Date.now() - d.getTime();
  const m = Math.floor(ms / 60_000);
  if (m < 1) return "à l’instant";
  if (m < 60) return `il y a ${m} min`;
  const h = Math.floor(m / 60);
  if (h < 24) return `il y a ${h} h`;
  const days = Math.floor(h / 24);
  return `il y a ${days} j`;
}

export function TodoDashboard() {
  const user = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const [allCollapsed, setAllCollapsed] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["todo-dashboard"],
    queryFn: () => todoDashboardService.getTodoDashboard(),
    staleTime: 5 * 60 * 1000,
    refetchInterval: 5 * 60 * 1000,
    refetchOnWindowFocus: true,
    enabled: user?.role !== "EMPLOYE",
  });

  const handleActionSuccess = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ["todo-dashboard"] });
    queryClient.invalidateQueries({ queryKey: ["sidebar-comptages"] });
  }, [queryClient]);

  const updatedRel = useMemo(() => (data?.generatedAt ? relativeFromIso(data.generatedAt) : ""), [data?.generatedAt]);

  if (user?.role === "EMPLOYE") return null;

  if (isLoading) {
    return <Skeleton className="h-[200px] w-full" />;
  }

  if (isError || !data) {
    return (
      <Card className="border-border">
        <CardHeader>
          <CardTitle className="text-base">📋 À faire aujourd&apos;hui</CardTitle>
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

  if (data.totalActionsGlobal === 0) {
    return (
      <Card className="border-border">
        <CardHeader>
          <CardTitle className="text-base">📋 À faire aujourd&apos;hui</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">✅ Tout est à jour — Aucune action requise aujourd&apos;hui</CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-border">
      <CardHeader className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <CardTitle className="text-base">📋 À faire aujourd&apos;hui</CardTitle>
            <p className="mt-1 text-xs text-muted-foreground">Mis à jour {updatedRel}.</p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-foreground">{data.totalActionsGlobal} actions</span>
            {data.totalActionsUrgentes > 0 ? (
              <Badge className="bg-red-600 text-white hover:bg-red-600">{data.totalActionsUrgentes} urgentes</Badge>
            ) : null}
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={refreshing}
              onClick={async () => {
                setRefreshing(true);
                await refetch();
                setTimeout(() => setRefreshing(false), 1000);
              }}
            >
              🔄 Actualiser
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => setAllCollapsed((v) => !v)}>
              {allCollapsed ? "Tout afficher ▼" : "Tout masquer ▲"}
            </Button>
          </div>

          {data.totalActionsGlobal > 10 ? (
            <Link href="/todo" className="text-sm font-medium text-indigo-700 hover:underline">
              Voir tout →
            </Link>
          ) : null}
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {data.sections.map((s) => (
          <TodoSection key={s.roleLabel} section={s} defaultOpen={!allCollapsed && s.totalActions > 0} onRefresh={handleActionSuccess} />
        ))}
      </CardContent>
    </Card>
  );
}

