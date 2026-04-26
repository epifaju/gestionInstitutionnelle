"use client";

import { cn } from "@/lib/utils";

type Opt = { code: string; label: string };

const PRIORITY: Opt[] = [
  { code: "EUR", label: "🇪🇺 EUR" },
  { code: "USD", label: "🇺🇸 USD" },
  { code: "GBP", label: "🇬🇧 GBP" },
  { code: "CHF", label: "🇨🇭 CHF" },
  { code: "XOF", label: "🇸🇳 XOF" },
  { code: "MAD", label: "🇲🇦 MAD" },
  { code: "DZD", label: "🇩🇿 DZD" },
  { code: "TND", label: "🇹🇳 TND" },
  { code: "JPY", label: "🇯🇵 JPY" },
  { code: "CNY", label: "🇨🇳 CNY" },
];

export function DeviseSelector({
  value,
  onChange,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  className?: string;
}) {
  return (
    <select
      className={cn("flex h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground", className)}
      value={value.toUpperCase()}
      onChange={(e) => onChange(e.target.value.toUpperCase())}
    >
      {PRIORITY.map((o) => (
        <option key={o.code} value={o.code}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

