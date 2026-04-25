"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { DeviseSelector } from "@/components/ui/DeviseSelector";
import { historiqueTaux } from "@/services/devises.service";
import { ResponsiveContainer, LineChart, Line, CartesianGrid, Tooltip, XAxis, YAxis } from "recharts";

type Pair = { base: string; cible: string };
const PAIRS: Pair[] = [
  { base: "EUR", cible: "USD" },
  { base: "EUR", cible: "GBP" },
  { base: "EUR", cible: "CHF" },
  { base: "EUR", cible: "XOF" },
];

export default function DevisesPage() {
  const t = useTranslations("Finance.tauxChange");
  const [pair, setPair] = useState<Pair>(PAIRS[0]);
  const [range, setRange] = useState<30 | 90 | 365>(90);

  const { debut, fin } = useMemo(() => {
    const f = new Date();
    const d = new Date();
    d.setDate(d.getDate() - range);
    return { debut: d.toISOString().slice(0, 10), fin: f.toISOString().slice(0, 10) };
  }, [range]);

  const { data, isLoading } = useQuery({
    queryKey: ["devises", "historique", pair.base, pair.cible, debut, fin],
    queryFn: () => historiqueTaux({ devise1: pair.base, devise2: pair.cible, debut, fin }),
  });

  const chart = (data ?? []).map((x) => ({
    date: x.dateTaux,
    taux: x.taux,
  }));

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{t("title")}</h1>
        <p className="text-sm text-slate-600">{t("subtitle")}</p>
      </div>

      <Card>
        <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <CardTitle className="text-base">
            {pair.base}/{pair.cible}
          </CardTitle>
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-2">
              <DeviseSelector value={pair.base} onChange={(v) => setPair((p) => ({ ...p, base: v }))} />
              <span className="text-sm text-slate-500">→</span>
              <DeviseSelector value={pair.cible} onChange={(v) => setPair((p) => ({ ...p, cible: v }))} />
            </div>
            <div className="flex rounded-md border border-slate-200 bg-white p-1">
              {[30, 90, 365].map((d) => (
                <button
                  key={d}
                  className={`px-3 py-1 text-sm rounded ${range === d ? "bg-slate-100 text-slate-900" : "text-slate-600"}`}
                  onClick={() => setRange(d as 30 | 90 | 365)}
                  type="button"
                >
                  {d}j
                </button>
              ))}
            </div>
          </div>
        </CardHeader>
        <CardContent className="h-80">
          {isLoading ? (
            <p className="text-sm text-slate-500">Chargement…</p>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chart}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-slate-200" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} domain={["auto", "auto"]} />
                <Tooltip formatter={(v) => Number(v).toFixed(6)} />
                <Line type="monotone" dataKey="taux" stroke="#4f46e5" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

