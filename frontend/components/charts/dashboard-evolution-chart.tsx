"use client";

import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

type Point = { mois: string; Dépenses: number; Recettes: number; Solde?: number };

function fmtEur(n: number) {
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(n);
}

export function DashboardEvolutionChart({ data }: { data: Point[] }) {
  const enriched = data.map((d) => ({
    ...d,
    Solde: d.Solde ?? d.Recettes - d.Dépenses,
  }));

  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={enriched}>
        <defs>
          <linearGradient id="recettesArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#10b981" stopOpacity={0.35} />
            <stop offset="100%" stopColor="#10b981" stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
        <XAxis dataKey="mois" tick={{ fontSize: 11 }} />
        <YAxis yAxisId="left" tick={{ fontSize: 11 }} />
        <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 11 }} />
        <Tooltip formatter={(v) => fmtEur(typeof v === "number" ? v : Number(v))} />
        <Legend />
        <Area
          yAxisId="left"
          type="monotone"
          dataKey="Recettes"
          fill="url(#recettesArea)"
          stroke="none"
          legendType="none"
        />
        <Bar yAxisId="left" dataKey="Dépenses" fill="#f43f5e" radius={[4, 4, 0, 0]} />
        <Bar yAxisId="left" dataKey="Recettes" fill="#10b981" radius={[4, 4, 0, 0]} />
        <Line
          yAxisId="right"
          type="monotone"
          dataKey="Solde"
          stroke="#0ea5e9"
          strokeWidth={2}
          dot={{ r: 3 }}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
