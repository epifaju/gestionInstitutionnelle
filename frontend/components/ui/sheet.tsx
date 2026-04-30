"use client";

import * as React from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

type SheetContextValue = {
  open: boolean;
  setOpen: (v: boolean) => void;
};

const SheetCtx = React.createContext<SheetContextValue | null>(null);

function useSheetCtx(component: string) {
  const ctx = React.useContext(SheetCtx);
  if (!ctx) throw new Error(`${component} must be used within <Sheet>`);
  return ctx;
}

export function Sheet({
  open,
  onOpenChange,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: React.ReactNode;
}) {
  const value = React.useMemo(() => ({ open, setOpen: onOpenChange }), [open, onOpenChange]);
  return <SheetCtx.Provider value={value}>{children}</SheetCtx.Provider>;
}

export function SheetContent({
  className,
  children,
  ...props
}: React.ComponentProps<"div">) {
  const { open, setOpen } = useSheetCtx("SheetContent");
  if (!open) return null;
  return (
    <>
      <div className="fixed inset-0 z-50 bg-black/40" onMouseDown={() => setOpen(false)} />
      <div
        className={cn(
          "fixed inset-y-0 right-0 z-50 w-full max-w-2xl overflow-y-auto border-l bg-card p-4 shadow-2xl",
          className
        )}
        role="dialog"
        aria-modal="true"
        onMouseDown={(e) => e.stopPropagation()}
        {...props}
      >
        <Button type="button" variant="ghost" size="icon" className="absolute right-2 top-2" onClick={() => setOpen(false)}>
          <X className="h-4 w-4" />
        </Button>
        {children}
      </div>
    </>
  );
}

export function SheetHeader({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("mb-3 space-y-1 pr-8", className)} {...props} />;
}

export function SheetTitle({ className, ...props }: React.ComponentProps<"h2">) {
  return <h2 className={cn("text-lg font-semibold leading-none tracking-tight text-foreground", className)} {...props} />;
}

export function SheetDescription({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("text-sm text-muted-foreground", className)} {...props} />;
}

