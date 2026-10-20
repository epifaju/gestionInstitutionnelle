"use client";

import dynamic from "next/dynamic";
import type { ComponentProps, ComponentType } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { DataTable } from "./DataTable";

type DataTableProps<T> = ComponentProps<typeof DataTable<T>>;

const DataTableDynamic = dynamic(() => import("./DataTable").then((m) => m.DataTable), {
  ssr: false,
  loading: () => <Skeleton className="h-64 w-full" />,
}) as ComponentType<DataTableProps<unknown>>;

/** Code-split @tanstack/react-table hors du chunk initial des pages listes. */
export function LazyDataTable<T>(props: DataTableProps<T>) {
  const Comp = DataTableDynamic as ComponentType<DataTableProps<T>>;
  return <Comp {...props} />;
}

export type { ServerPagination } from "./DataTable";
