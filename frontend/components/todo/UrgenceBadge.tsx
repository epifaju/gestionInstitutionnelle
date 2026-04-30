"use client";

import { Badge } from "@/components/ui/badge";

export function UrgenceBadge({ niveau }: { niveau: "CRITIQUE" | "URGENT" | "NORMAL" }) {
  if (niveau === "CRITIQUE") {
    return (
      <Badge className="bg-red-900 text-white hover:bg-red-900">
        <span className="mr-1 inline-flex h-2 w-2 items-center justify-center">
          <span className="h-2 w-2 animate-pulse rounded-full bg-white/90" />
        </span>
        ⚠️ Critique
      </Badge>
    );
  }
  if (niveau === "URGENT") {
    return <Badge className="bg-orange-500 text-white hover:bg-orange-500">⚡ Urgent</Badge>;
  }
  return (
    <Badge variant="secondary" className="text-xs">
      Normal
    </Badge>
  );
}

