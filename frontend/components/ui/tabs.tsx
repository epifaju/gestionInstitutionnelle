"use client";

import * as React from "react";

import { cn } from "@/lib/utils";

type TabsContextValue = {
  value: string;
  setValue: (v: string) => void;
};

const TabsCtx = React.createContext<TabsContextValue | null>(null);

function useTabsCtx(component: string) {
  const ctx = React.useContext(TabsCtx);
  if (!ctx) throw new Error(`${component} must be used within <Tabs>`);
  return ctx;
}

export function Tabs({
  value,
  onValueChange,
  defaultValue,
  children,
  className,
}: {
  value?: string;
  onValueChange?: (v: string) => void;
  defaultValue?: string;
  children: React.ReactNode;
  className?: string;
}) {
  const isControlled = value !== undefined;
  const [inner, setInner] = React.useState(defaultValue ?? "");
  const current = isControlled ? (value as string) : inner;
  const setValue = React.useCallback(
    (v: string) => {
      if (!isControlled) setInner(v);
      onValueChange?.(v);
    },
    [isControlled, onValueChange]
  );

  const memo = React.useMemo(() => ({ value: current, setValue }), [current, setValue]);

  return (
    <TabsCtx.Provider value={memo}>
      <div className={cn("w-full", className)}>{children}</div>
    </TabsCtx.Provider>
  );
}

export function TabsList({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      role="tablist"
      className={cn("inline-flex h-9 flex-wrap items-center justify-start gap-1 rounded-lg bg-muted p-1 text-muted-foreground", className)}
      {...props}
    />
  );
}

export function TabsTrigger({
  className,
  value,
  ...props
}: React.ComponentProps<"button"> & { value: string }) {
  const { value: active, setValue } = useTabsCtx("TabsTrigger");
  const selected = active === value;
  return (
    <button
      type="button"
      role="tab"
      aria-selected={selected}
      data-state={selected ? "active" : "inactive"}
      className={cn(
        "inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50",
        selected ? "bg-background text-foreground shadow-sm" : "hover:bg-background/60 hover:text-foreground",
        className
      )}
      onClick={() => setValue(value)}
      {...props}
    />
  );
}

export function TabsContent({
  className,
  value,
  ...props
}: React.ComponentProps<"div"> & { value: string }) {
  const { value: active } = useTabsCtx("TabsContent");
  if (active !== value) return null;
  return (
    <div
      role="tabpanel"
      data-state="active"
      className={cn("mt-4 ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2", className)}
      {...props}
    />
  );
}
