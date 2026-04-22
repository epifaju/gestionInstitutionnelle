"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { listTauxChange, upsertTauxChange } from "@/services/taux-change.service";

type Devise = "EUR" | "XOF" | "USD";

function todayIso() {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

export default function TauxChangePage() {
  const t = useTranslations("Finance.tauxChange");
  const tc = useTranslations("Common");
  const qc = useQueryClient();

  const [date, setDate] = useState(todayIso());
  const devises = useMemo<Devise[]>(() => ["EUR", "XOF", "USD"], []);

  const { data, isLoading, error } = useQuery({
    queryKey: ["finance", "taux-change", date],
    queryFn: () => listTauxChange(date),
  });

  const initialMap = useMemo(() => {
    const m = new Map<string, string>();
    m.set("EUR", "1");
    for (const r of data ?? []) {
      m.set(String(r.devise).toUpperCase(), String(r.tauxVersEur));
    }
    return m;
  }, [data]);

  const [values, setValues] = useState<Record<string, string>>({ EUR: "1", XOF: "", USD: "" });

  // Re-sync map -> inputs when date changes / query resolves
  useMemo(() => {
    const next: Record<string, string> = { EUR: initialMap.get("EUR") ?? "1", XOF: initialMap.get("XOF") ?? "", USD: initialMap.get("USD") ?? "" };
    setValues(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date, isLoading]);

  const mut = useMutation({
    mutationFn: async () => {
      const ops = devises.map((devise) => {
        const v = devise === "EUR" ? "1" : (values[devise] ?? "").trim();
        if (devise !== "EUR" && !v) return null;
        return upsertTauxChange({ date, devise, tauxVersEur: v || "1" });
      }).filter(Boolean) as Promise<unknown>[];
      await Promise.all(ops);
    },
    onSuccess: async () => {
      toast.success(tc("successSaved"));
      await qc.invalidateQueries({ queryKey: ["finance", "taux-change", date] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{t("title")}</h1>
        <p className="mt-1 text-sm text-slate-600">{t("subtitle")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t("dateTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap items-end gap-3">
          <div className="min-w-[14rem]">
            <Label htmlFor="date">{t("dateLabel")}</Label>
            <Input id="date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <Button type="button" onClick={() => mut.mutate()} disabled={mut.isPending}>
            {tc("save")}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t("ratesTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {isLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full max-w-md" />
              <Skeleton className="h-10 w-full max-w-md" />
              <Skeleton className="h-10 w-full max-w-md" />
            </div>
          ) : error ? (
            <p className="text-sm text-red-600">{tc("errorGeneric")}</p>
          ) : (
            devises.map((devise) => (
              <div key={devise} className="flex items-center justify-between gap-3 rounded-md border border-slate-200 bg-white p-3">
                <div>
                  <p className="text-sm font-medium text-slate-900">{devise}</p>
                  <p className="text-xs text-slate-500">{t("rateHint", { devise })}</p>
                </div>
                <Input
                  className="w-40 text-right"
                  inputMode="decimal"
                  disabled={devise === "EUR"}
                  value={devise === "EUR" ? "1" : (values[devise] ?? "")}
                  onChange={(e) => setValues((m) => ({ ...m, [devise]: e.target.value }))}
                  placeholder={devise === "EUR" ? "1" : "0.000000"}
                />
              </div>
            ))
          )}
          <p className="text-xs text-slate-500">{t("footer")}</p>
        </CardContent>
      </Card>
    </div>
  );
}

