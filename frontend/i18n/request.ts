import { getRequestConfig } from "next-intl/server";
import { cookies } from "next/headers";

export default getRequestConfig(async () => ({
  locale: cookies().get("locale")?.value || "fr",
  messages: (
    await import(
      `../messages/${
        cookies().get("locale")?.value === "en"
          ? "en"
          : cookies().get("locale")?.value === "pt-PT"
            ? "pt-PT"
            : "fr"
      }.json`
    )
  ).default,
}));
