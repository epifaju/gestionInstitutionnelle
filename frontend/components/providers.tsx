"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NextIntlClientProvider } from "next-intl";
import { Toaster } from "sonner";
import { useState, type ReactNode } from "react";
import fr from "@/messages/fr.json";
import en from "@/messages/en.json";
import ptPT from "@/messages/pt-PT.json";
import { LocaleProvider, useAppLocale } from "@/lib/locale-context";
import { ThemeProvider } from "@/lib/theme-context";
import { GlobalLoading } from "@/components/global-loading";

type Messages = Record<string, unknown>;

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

function IntlBridge({ children }: { children: ReactNode }) {
  const { locale } = useAppLocale();
  const messages = locale === "en" ? en : locale === "pt-PT" ? deepMerge(fr as Messages, ptPT as Messages) : fr;

  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      {children}
      <Toaster richColors position="top-right" closeButton />
      <GlobalLoading />
    </NextIntlClientProvider>
  );
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: 1, refetchOnWindowFocus: false },
        },
      })
  );

  return (
    <LocaleProvider>
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <IntlBridge>{children}</IntlBridge>
        </QueryClientProvider>
      </ThemeProvider>
    </LocaleProvider>
  );
}
