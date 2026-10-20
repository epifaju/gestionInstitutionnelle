"use client";

import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

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
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="cat" tick={{ fontSize: 10 }} interval={0} angle={-25} textAnchor="end" height={80} />
        <YAxis tick={{ fontSize: 11 }} />
        <Tooltip formatter={(v) => formatValue(typeof v === "number" ? v : Number(v))} />
        <Legend />
        <Bar dataKey="planned" name={plannedLabel} fill={plannedFill} />
        <Bar dataKey="actual" name={actualLabel} fill={actualFill} />
      </BarChart>
    </ResponsiveContainer>
  );
}
