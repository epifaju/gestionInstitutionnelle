"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";

const chartFallback = <Skeleton className="h-full min-h-[16rem] w-full" />;

export const LazyDashboardEvolutionChart = dynamic(
  () => import("./dashboard-evolution-chart").then((m) => m.DashboardEvolutionChart),
  { ssr: false, loading: () => chartFallback }
);

export const LazyBudgetCompareChart = dynamic(
  () => import("./budget-compare-chart").then((m) => m.BudgetCompareChart),
  { ssr: false, loading: () => chartFallback }
);

export const LazyDevisesHistoryChart = dynamic(
  () => import("./devises-history-chart").then((m) => m.DevisesHistoryChart),
  { ssr: false, loading: () => chartFallback }
);

export const LazyBudgetExecutionChart = dynamic(
  () => import("./budget-execution-chart").then((m) => m.BudgetExecutionChart),
  { ssr: false, loading: () => chartFallback }
);

export const LazyInventaireRepartitionChart = dynamic(
  () => import("./inventaire-repartition-chart").then((m) => m.InventaireRepartitionChart),
  { ssr: false, loading: () => chartFallback }
);
