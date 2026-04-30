"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { AxiosError } from "axios";
import { Building2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { get, post } from "@/lib/api";
import { getDefaultHomePath } from "@/lib/post-login";
import { useAuthStore } from "@/lib/store";
import { setLocaleCookie } from "@/lib/locale-cookie";
import { useAppLocale } from "@/lib/locale-context";
import { useAppTheme } from "@/lib/theme-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: {
    id: string;
    email: string;
    nom: string | null;
    prenom: string | null;
    role: string;
    organisationId: string;
    organisationNom: string | null;
    langue?: string | null;
  };
};

type ErrorBody = { code?: string; message?: string };

export default function LoginPage() {
  const t = useTranslations("Login");
  const tv = useTranslations("Validation");
  const tc = useTranslations("Common");
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [error, setError] = useState<string | null>(null);
  const { locale, setLocale } = useAppLocale();
  const { setTheme } = useAppTheme();

  const schema = useMemo(
    () =>
      z.object({
        email: z.string().min(1, tv("required")).email(tv("email")),
        password: z.string().min(8, tv("minLength")),
      }),
    [tv]
  );

  type FormValues = z.infer<typeof schema>;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(values: FormValues) {
    setError(null);
    try {
      const data = await post<LoginResponse>("auth/login", values);
      setAuth(data.user, data.accessToken);
      // Applique la langue préférée après login
      if (typeof window !== "undefined" && data.user.langue) {
        const { setLocaleCookie } = await import("@/lib/locale-cookie");
        const l = data.user.langue === "pt_pt" ? "pt-PT" : data.user.langue;
        if (l === "fr" || l === "en" || l === "pt-PT") {
          setLocaleCookie(l);
          setLocale(l);
        }
      }
      // Applique le thème de l'utilisateur avant redirection (évite le "flash")
      try {
        const prefs = await get<{ theme: "system" | "light" | "dark" }>("auth/me/preferences");
        if (prefs?.theme) setTheme(prefs.theme);
      } catch {
        /* ignore */
      }
      router.push(getDefaultHomePath(data.user.role));
    } catch (e) {
      const err = e as AxiosError<ErrorBody>;
      const code = err.response?.data?.code;
      if (code === "IDENTIFIANTS_INCORRECTS") {
        setError(t("errorCredentials"));
      } else {
        setError(err.response?.data?.message ?? "Une erreur est survenue.");
      }
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-950 via-indigo-950 to-slate-900 p-4">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center text-center text-white">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-background/10 ring-1 ring-border/40">
            <Building2 className="h-8 w-8 text-indigo-200" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
          <p className="mt-2 text-sm text-indigo-100/80">{t("subtitle")}</p>
          <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
            <Button
              type="button"
              size="sm"
              variant={locale === "fr" ? "secondary" : "ghost"}
              onClick={() => {
                setLocaleCookie("fr");
                setLocale("fr");
              }}
            >
              {tc("localeFr")}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={locale === "en" ? "secondary" : "ghost"}
              onClick={() => {
                setLocaleCookie("en");
                setLocale("en");
              }}
            >
              {tc("localeEn")}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={locale === "pt-PT" ? "secondary" : "ghost"}
              onClick={() => {
                setLocaleCookie("pt-PT");
                setLocale("pt-PT");
              }}
            >
              {tc("localePt")}
            </Button>
          </div>
        </div>

        <Card className="border-border shadow-xl shadow-indigo-950/40">
          <CardHeader>
            <CardTitle className="text-foreground">{t("title")}</CardTitle>
            <CardDescription>{t("cardDescription")}</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)}>
              <div className="space-y-2">
                <Label htmlFor="email">{t("email")}</Label>
                <Controller
                  name="email"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Input
                      {...field}
                      id="email"
                      type="email"
                      autoComplete="email"
                      value={field.value ?? ""}
                      aria-invalid={fieldState.invalid}
                    />
                  )}
                />
                {form.formState.errors.email ? (
                  <p className="text-xs text-red-600">{form.formState.errors.email.message}</p>
                ) : null}
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">{t("password")}</Label>
                <Controller
                  name="password"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Input
                      {...field}
                      id="password"
                      type="password"
                      autoComplete="current-password"
                      value={field.value ?? ""}
                      aria-invalid={fieldState.invalid}
                    />
                  )}
                />
                {form.formState.errors.password ? (
                  <p className="text-xs text-red-600">{form.formState.errors.password.message}</p>
                ) : null}
              </div>
              {error ? <p className="text-sm text-red-600">{error}</p> : null}
              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? "…" : t("submit")}
              </Button>
              <p className="text-center text-sm">
                <a href="/forgot-password" className="text-indigo-600 hover:underline">
                  {t("forgotPassword")}
                </a>
              </p>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
