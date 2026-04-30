"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

export function Progress({
  value,
  className,
  ...props
}: React.ComponentProps<"div"> & { value?: number | null }) {
  const v = Math.max(0, Math.min(100, value ?? 0));
  return (
    <div
      className={cn("relative h-2 w-full overflow-hidden rounded-full bg-muted", className)}
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={v}
      {...props}
    >
      <div className="h-full bg-indigo-600 transition-[width]" style={{ width: `${v}%` }} />
    </div>
  );
}

