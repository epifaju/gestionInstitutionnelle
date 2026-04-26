"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import Link from "next/link";
import { useMemo, useState } from "react";
import { Building2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { forgotPassword } from "@/services/password.service";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function ForgotPasswordPage() {
  const t = useTranslations("ForgotPassword");
  const tv = useTranslations("Validation");
  const [done, setDone] = useState(false);

  const schema = useMemo(() => z.object({ email: z.string().min(1, tv("required")).email(tv("email")) }), [tv]);
  type FormValues = z.infer<typeof schema>;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "" },
  });

  async function onSubmit(values: FormValues) {
    try {
      const res = await forgotPassword(values.email);
      toast.success(res.message);
      setDone(true);
    } catch {
      /* toast via api interceptor */
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
        </div>

        <Card className="border-border shadow-xl shadow-indigo-950/40">
          <CardHeader>
            <CardTitle className="text-foreground">{t("title")}</CardTitle>
            <CardDescription>{t("description")}</CardDescription>
          </CardHeader>
          <CardContent>
            {done ? (
              <p className="text-sm text-muted-foreground">{t("doneHint")}</p>
            ) : (
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
                <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                  {form.formState.isSubmitting ? "…" : t("submit")}
                </Button>
              </form>
            )}
            <p className="mt-4 text-center text-sm text-muted-foreground">
              <Link href="/login" className="text-indigo-600 hover:underline">
                {t("backLogin")}
              </Link>
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
