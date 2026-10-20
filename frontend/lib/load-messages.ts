import type { AppLocale } from "@/lib/locale-context";

export type Messages = Record<string, unknown>;

function deepMerge(base: Messages, override: Messages): Messages {
  const out: Messages = { ...base };
  for (const [k, v] of Object.entries(override)) {
    const bv = out[k];
    if (bv && typeof bv === "object" && !Array.isArray(bv) && typeof v === "object" && v && !Array.isArray(v)) {
      out[k] = deepMerge(bv as Messages, v as Messages);
    } else {
      out[k] = v;
    }
  }
  return out;
}

/** Charge uniquement le catalogue de la locale demandée (code-split webpack). */
export async function loadMessages(locale: AppLocale): Promise<Messages> {
  if (locale === "en") {
    return (await import("@/messages/en.json")).default as Messages;
  }
  if (locale === "pt-PT") {
    const [{ default: fr }, { default: pt }] = await Promise.all([
      import("@/messages/fr.json"),
      import("@/messages/pt-PT.json"),
    ]);
    return deepMerge(fr as Messages, pt as Messages);
  }
  return (await import("@/messages/fr.json")).default as Messages;
}
