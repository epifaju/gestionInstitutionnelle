export function normalizeNotificationLink(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const link = raw.trim();
  if (!link) return null;

  // Backward-compat for early notification links that pointed to non-existing detail pages.
  if (link.startsWith("/inventaire/biens/")) return "/inventaire";

  return link;
}

