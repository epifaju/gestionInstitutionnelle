import { getRequestConfig } from "next-intl/server";
import { cookies } from "next/headers";

export default getRequestConfig(async () => {
  const cookieStore = await cookies();
  const locale =
    cookieStore.get("locale")?.value === "en"
      ? "en"
      : cookieStore.get("locale")?.value === "pt-PT"
        ? "pt-PT"
        : "fr";

  return {
    locale,
    // Avoid ENVIRONMENT_FALLBACK markup mismatches in Docker/CI builds.
    timeZone: "Europe/Paris",
    messages: (await import(`../messages/${locale}.json`)).default,
  };
});
