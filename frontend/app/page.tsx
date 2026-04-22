import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { decodeJwtPayload } from "@/lib/jwt";
import { getDefaultHomePath } from "@/lib/post-login";

export default function Home() {
  const token = cookies().get("access_token")?.value;
  if (token) {
    const raw = decodeURIComponent(token);
    const payload = decodeJwtPayload(raw);
    redirect(getDefaultHomePath(payload?.role));
  }
  redirect("/login");
}
