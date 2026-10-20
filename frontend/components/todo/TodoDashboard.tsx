"use client";

import { useCallback, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import Link from "next/link";

import { todoDashboardService } from "@/services/todo-dashboard.service";
import { useAuthStore } from "@/lib/store";
import { formatTodoRelative } from "@/lib/todo-i18n";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { TodoSection } from "@/components/todo/TodoSection";

export function TodoDashboard() {
  const t = useTranslations("Todo");
  const tc = useTranslations("Common");
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

  const updatedRel = useMemo(
    () => (data?.generatedAt ? formatTodoRelative(t, data.generatedAt) : ""),
    [data?.generatedAt, t]
  );

  if (user?.role === "EMPLOYE") return null;

  if (isLoading) {
    return <Skeleton className="h-[200px] w-full" />;
  }

  if (isError || !data) {
    return (
      <Card className="border-border">
        <CardHeader>
          <CardTitle className="text-base">📋 {t("titleToday")}</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">{t("loadError")}</p>
          <Button type="button" variant="outline" size="sm" onClick={() => refetch()}>
            {tc("retry")}
          </Button>
        </CardContent>
      </Card>
    );
  }

  if (data.totalActionsGlobal === 0) {
    return (
      <Card className="border-border">
        <CardHeader>
          <CardTitle className="text-base">📋 {t("titleToday")}</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">✅ {t("emptyToday")}</CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-border">
      <CardHeader className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <CardTitle className="text-base">📋 {t("titleToday")}</CardTitle>
            <p className="mt-1 text-xs text-muted-foreground">{t("updated", { rel: updatedRel })}</p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-foreground">{t("summaryActions", { count: data.totalActionsGlobal })}</span>
            {data.totalActionsUrgentes > 0 ? (
              <Badge className="bg-red-600 text-white hover:bg-red-600">
                {t("urgentesBadge", { count: data.totalActionsUrgentes })}
              </Badge>
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
              🔄 {t("refresh")}
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => setAllCollapsed((v) => !v)}>
              {allCollapsed ? t("expandAll") : t("collapseAll")}
            </Button>
          </div>

          {data.totalActionsGlobal > 10 ? (
            <Link href="/todo" className="text-sm font-medium text-indigo-700 hover:underline">
              {t("seeAll")}
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
