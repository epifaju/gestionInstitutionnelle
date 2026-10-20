"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NextIntlClientProvider } from "next-intl";
import { Toaster } from "sonner";
import { useEffect, useState, type ReactNode } from "react";
import fr from "@/messages/fr.json";
import { LocaleProvider, useAppLocale, type AppLocale } from "@/lib/locale-context";
import { ThemeProvider } from "@/lib/theme-context";
import { GlobalLoading } from "@/components/global-loading";
import { loadMessages, type Messages } from "@/lib/load-messages";

function IntlBridge({ children }: { children: ReactNode }) {
  const { locale } = useAppLocale();
  // FR synchronisé au boot ; EN / pt-PT chargés à la demande (code-split).
  const [messages, setMessages] = useState<Messages>(() => fr as Messages);
  const [activeLocale, setActiveLocale] = useState<AppLocale>("fr");

  useEffect(() => {
    let cancelled = false;
    if (locale === "fr") {
      setMessages(fr as Messages);
      setActiveLocale("fr");
      return;
    }
    loadMessages(locale).then((m) => {
      if (!cancelled) {
        setMessages(m);
        setActiveLocale(locale);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [locale]);

  return (
    <NextIntlClientProvider locale={activeLocale} messages={messages} timeZone="Europe/Paris">
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
          queries: {
            retry: 1,
            refetchOnWindowFocus: false,
            staleTime: 60_000,
          },
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
