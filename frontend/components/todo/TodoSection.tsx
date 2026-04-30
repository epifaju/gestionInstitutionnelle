"use client";

import { useEffect, useMemo, useState } from "react";

import type { TodoSection as TodoSectionT } from "@/services/todo-dashboard.service";
import { TodoActionGroup } from "@/components/todo/TodoActionGroup";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

export function TodoSection({
  section,
  defaultOpen,
  onRefresh,
}: {
  section: TodoSectionT;
  defaultOpen?: boolean;
  onRefresh: () => void;
}) {
  const openByDefault = useMemo(() => defaultOpen ?? section.totalActions > 0, [defaultOpen, section.totalActions]);
  const [open, setOpen] = useState(openByDefault);

  useEffect(() => {
    setOpen(openByDefault);
  }, [openByDefault]);

  return (
    <Card className="border-border">
      <CardContent className="p-4">
        <div className="flex w-full items-start justify-between gap-3 text-left">
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-foreground">
              {open ? "▼" : "▶"} {section.icone} {section.roleLabel}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              {section.totalActions} action(s) dont {section.actionsUrgentes} urgente(s)
            </p>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            {section.actionsUrgentes > 0 ? <Badge className="bg-red-600 text-white hover:bg-red-600">Urgent</Badge> : null}
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => setOpen((v) => !v)}
              aria-expanded={open}
              aria-label={open ? "Réduire la section" : "Ouvrir la section"}
            >
              {open ? "▼ réduire" : "▼ ouvrir"}
            </Button>
          </div>
        </div>

        {!open ? null : section.totalActions === 0 ? (
          <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-900 dark:border-emerald-900/30 dark:bg-emerald-950/30 dark:text-emerald-100">
            ✅ Aucune action requise pour l&apos;instant
          </div>
        ) : (
          <div className="mt-4 space-y-3">
            {section.groupes.map((g) => (
              <TodoActionGroup key={g.categorie} groupe={g} onRefresh={onRefresh} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

