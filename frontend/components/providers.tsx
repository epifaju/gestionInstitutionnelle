"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NextIntlClientProvider } from "next-intl";
import { Toaster } from "sonner";
import { useState, type ReactNode } from "react";
import fr from "@/messages/fr.json";
import en from "@/messages/en.json";
import { LocaleProvider, useAppLocale } from "@/lib/locale-context";
import { GlobalLoading } from "@/components/global-loading";

function IntlBridge({ children }: { children: ReactNode }) {
  const { locale } = useAppLocale();
  const messages = locale === "en" ? en : fr;

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
      <QueryClientProvider client={queryClient}>
        <IntlBridge>{children}</IntlBridge>
      </QueryClientProvider>
    </LocaleProvider>
  );
}
