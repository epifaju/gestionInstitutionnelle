"use client";

import * as React from "react";
import { X } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

type DialogContextValue = {
  open: boolean;
  setOpen: (v: boolean) => void;
};

const DialogCtx = React.createContext<DialogContextValue | null>(null);

function useDialogCtx(component: string) {
  const ctx = React.useContext(DialogCtx);
  if (!ctx) throw new Error(`${component} must be used within <Dialog>`);
  return ctx;
}

export function Dialog({
  open,
  onOpenChange,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: React.ReactNode;
}) {
  const value = React.useMemo(() => ({ open, setOpen: onOpenChange }), [open, onOpenChange]);

  React.useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onOpenChange(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onOpenChange]);

  return <DialogCtx.Provider value={value}>{children}</DialogCtx.Provider>;
}

export function DialogTrigger({ asChild, children }: { asChild?: boolean; children: React.ReactNode }) {
  const { setOpen } = useDialogCtx("DialogTrigger");
  if (asChild && React.isValidElement(children)) {
    return React.cloneElement(children as React.ReactElement<{ onClick?: (e: unknown) => void }>, {
      onClick: (e: unknown) => {
        (children as React.ReactElement<{ onClick?: (ev: unknown) => void }>).props.onClick?.(e);
        setOpen(true);
      },
    });
  }
  return (
    <button type="button" className="inline-flex" onClick={() => setOpen(true)}>
      {children}
    </button>
  );
}

export function DialogPortal({ children }: { children: React.ReactNode }) {
  const { open } = useDialogCtx("DialogPortal");
  if (!open) return null;
  return <>{children}</>;
}

export function DialogOverlay({ className, ...props }: React.ComponentProps<"div">) {
  const { setOpen } = useDialogCtx("DialogOverlay");
  return (
    <div
      className={cn("fixed inset-0 z-50 bg-black/40", className)}
      onMouseDown={() => setOpen(false)}
      {...props}
    />
  );
}

export function DialogContent({
  className,
  children,
  showClose = true,
  ...props
}: React.ComponentProps<"div"> & { showClose?: boolean }) {
  const { setOpen } = useDialogCtx("DialogContent");
  return (
    <DialogPortal>
      <DialogOverlay />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onMouseDown={() => setOpen(false)}>
        <div
          role="dialog"
          aria-modal="true"
          className={cn(
            "relative z-50 w-full max-w-lg overflow-hidden rounded-lg border border-border bg-card p-4 text-card-foreground shadow-xl",
            className
          )}
          onMouseDown={(e) => e.stopPropagation()}
          {...props}
        >
          {showClose ? (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="absolute right-2 top-2"
              aria-label="Fermer"
              onClick={() => setOpen(false)}
            >
              <X className="h-4 w-4" />
            </Button>
          ) : null}
          {children}
        </div>
      </div>
    </DialogPortal>
  );
}

export function DialogHeader({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("mb-3 space-y-1 pr-8", className)} {...props} />;
}

export function DialogFooter({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("mt-4 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end", className)} {...props} />;
}

export function DialogTitle({ className, ...props }: React.ComponentProps<"h2">) {
  return <h2 className={cn("text-lg font-semibold leading-none tracking-tight text-foreground", className)} {...props} />;
}

export function DialogDescription({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("text-sm text-muted-foreground", className)} {...props} />;
}
