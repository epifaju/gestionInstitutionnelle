"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

const variants: Record<string, string> = {
  default: "bg-muted text-foreground",
  success: "bg-emerald-100 text-emerald-800",
  warning: "bg-amber-100 text-amber-900",
  muted: "bg-muted text-muted-foreground",
  danger: "bg-red-100 text-red-800",
  dangerSolid: "bg-red-600 text-white",
  info: "bg-sky-100 text-sky-900",
  orange: "bg-orange-100 text-orange-900",
};

export function Badge({
  children,
  variant = "default",
  className,
}: {
  children: ReactNode;
  variant?: keyof typeof variants;
  className?: string;
}) {
  return (
    <span
      className={cn("inline-flex rounded-full px-2 py-0.5 text-xs font-medium", variants[variant], className)}
    >
      {children}
    </span>
  );
}
