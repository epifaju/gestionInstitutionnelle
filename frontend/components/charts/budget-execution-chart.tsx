"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

type Point = { categorie: string; taux: number; alerte: boolean };

const FILL_OK = "#10b981";
const FILL_ALERT = "#f43f5e";

export function BudgetExecutionChart({
  data,
  formatPct,
}: {
  data: Point[];
  formatPct: (n: number) => string;
}) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} layout="vertical" margin={{ left: 8, right: 16 }}>
        <CartesianGrid strokeDasharray="3 3" className="stroke-border" horizontal={false} />
        <XAxis type="number" domain={[0, "dataMax"]} tick={{ fontSize: 11 }} unit="%" />
        <YAxis type="category" dataKey="categorie" width={110} tick={{ fontSize: 11 }} />
        <Tooltip formatter={(v) => formatPct(typeof v === "number" ? v : Number(v))} />
        <Bar dataKey="taux" name="%" radius={[0, 4, 4, 0]}>
          {data.map((d) => (
            <Cell key={d.categorie} fill={d.alerte ? FILL_ALERT : FILL_OK} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
