"use client";

export function setLocaleCookie(locale: "fr" | "en" | "pt-PT") {
  // Cookie simple côté client, lu par next-intl (request.ts)
  document.cookie = `locale=${encodeURIComponent(locale)}; Path=/; Max-Age=${60 * 60 * 24 * 365}`;
}

