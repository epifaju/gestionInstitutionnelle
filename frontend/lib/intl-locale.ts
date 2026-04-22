import type { AppLocale } from "@/lib/locale-context";

/** Map app locales to BCP-47 tags for Intl formatters (dates, numbers, currency). */
export function intlLocaleTag(locale: AppLocale | string): string {
  if (locale === "pt-PT") return "pt-PT";
  if (locale === "en") return "en-GB";
  return "fr-FR";
}
