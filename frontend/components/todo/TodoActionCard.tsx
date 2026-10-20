"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import type { TodoActionItem } from "@/services/todo-dashboard.service";
import { formatTodoRelative } from "@/lib/todo-i18n";
import { UrgenceBadge } from "@/components/todo/UrgenceBadge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";

export interface ActionDisponible {
  typeAction: string;
  label: string;
  variante: "success" | "danger" | "default";
  requiresComment?: boolean;
}

function btnVariant(v: ActionDisponible["variante"]): "default" | "destructive" | "outline" | "secondary" {
  if (v === "success") return "default";
  if (v === "danger") return "destructive";
  return "outline";
}

export function TodoActionCard({
  item,
  onAction,
  isLoading,
  actionsDisponibles = [],
}: {
  item: TodoActionItem;
  onAction?: (typeAction: string, commentaire?: string) => Promise<void>;
  isLoading?: boolean;
  actionsDisponibles?: ActionDisponible[];
}) {
  const t = useTranslations("Todo");
  const tc = useTranslations("Common");
  const router = useRouter();
  const [isActioning, setIsActioning] = useState(false);
  const [showCommentDialog, setShowCommentDialog] = useState(false);
  const [commentaire, setCommentaire] = useState("");
  const [pendingAction, setPendingAction] = useState<ActionDisponible | null>(null);

  const rel = useMemo(
    () => formatTodoRelative(t, item.dateCreation, { withJustNow: false }),
    [item.dateCreation, t]
  );

  async function runAction(a: ActionDisponible, comment?: string) {
    if (!onAction) return;
    setIsActioning(true);
    try {
      await onAction(a.typeAction, comment);
      toast.success(t("actionSuccess"));
    } catch (e) {
      toast.error((e as Error)?.message ?? t("actionImpossible"));
    } finally {
      setIsActioning(false);
      setCommentaire("");
      setPendingAction(null);
      setShowCommentDialog(false);
    }
  }

  if (isLoading) {
    return <Skeleton className="h-28 w-full" />;
  }

  return (
    <>
      <Card className="border-border">
        <CardContent className="p-3">
          <div className="flex items-start justify-between gap-3">
            <UrgenceBadge niveau={item.niveauUrgence} />
            <span className="text-xs text-muted-foreground">{rel}</span>
          </div>

          <div className="mt-2 space-y-1">
            <p className="truncate text-sm font-medium text-foreground">{item.titre}</p>
            <p className="truncate text-xs text-muted-foreground">{item.sousTitre}</p>
          </div>

          <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
            <div className="flex flex-wrap gap-2">
              {actionsDisponibles.map((a) => (
                <Button
                  key={a.typeAction}
                  type="button"
                  size="sm"
                  variant={btnVariant(a.variante)}
                  disabled={!onAction || isActioning}
                  onClick={() => {
                    if (a.requiresComment) {
                      setPendingAction(a);
                      setShowCommentDialog(true);
                      return;
                    }
                    void runAction(a);
                  }}
                >
                  {a.label}
                </Button>
              ))}
            </div>

            <Button type="button" size="sm" variant="ghost" disabled={isActioning} onClick={() => router.push(item.lienAction)}>
              {t("seeDetail")}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Dialog open={showCommentDialog} onOpenChange={setShowCommentDialog}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t("commentRequired")}</DialogTitle>
            <DialogDescription>{t("commentHint")}</DialogDescription>
          </DialogHeader>
          <Textarea value={commentaire} onChange={(e) => setCommentaire(e.target.value)} placeholder={t("commentPlaceholder")} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setShowCommentDialog(false)} disabled={isActioning}>
              {tc("cancel")}
            </Button>
            <Button
              type="button"
              variant={pendingAction?.variante === "danger" ? "destructive" : "default"}
              disabled={isActioning || !pendingAction || commentaire.trim().length === 0}
              onClick={() => {
                if (!pendingAction) return;
                void runAction(pendingAction, commentaire.trim());
              }}
            >
              {tc("confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
