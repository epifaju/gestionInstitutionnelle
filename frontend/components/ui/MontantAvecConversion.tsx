"use client";

function fmt(v: string | number) {
  const n = typeof v === "string" ? parseFloat(v) : v;
  return Number.isNaN(n) ? String(v) : n.toFixed(2);
}

export function MontantAvecConversion({
  montant,
  devise,
  montantEur,
  date,
}: {
  montant: string | number;
  devise: string;
  montantEur: string | number;
  date?: string | null;
}) {
  const title = `≈ ${fmt(montantEur)} EUR au taux du ${date ?? "jour"}`;
  return (
    <span className="tabular-nums" title={devise.toUpperCase() === "EUR" ? undefined : title}>
      {fmt(montant)} {devise.toUpperCase()}
    </span>
  );
}

