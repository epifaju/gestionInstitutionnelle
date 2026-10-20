"use client";

import {
  Bar,
  BarChart,
  Brush,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

type Point = { cat: string; planned: number; actual: number };

export function BudgetCompareChart({
  data,
  plannedLabel,
  actualLabel,
  plannedFill,
  actualFill,
  formatValue,
}: {
  data: Point[];
  plannedLabel: string;
  actualLabel: string;
  plannedFill: string;
  actualFill: string;
  formatValue: (n: number) => string;
}) {
  const showBrush = data.length > 6;

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ bottom: showBrush ? 8 : 0 }}>
        <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
        <XAxis dataKey="cat" tick={{ fontSize: 10 }} interval={0} angle={-25} textAnchor="end" height={80} />
        <YAxis tick={{ fontSize: 11 }} />
        <Tooltip formatter={(v) => formatValue(typeof v === "number" ? v : Number(v))} />
        <Legend />
        <Bar dataKey="planned" name={plannedLabel} fill={plannedFill} radius={[4, 4, 0, 0]} />
        <Bar dataKey="actual" name={actualLabel} fill={actualFill} radius={[4, 4, 0, 0]} />
        {showBrush ? <Brush dataKey="cat" height={22} stroke="#94a3b8" travellerWidth={8} /> : null}
      </BarChart>
    </ResponsiveContainer>
  );
}
