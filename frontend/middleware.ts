import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { decodeJwtPayload } from "./lib/jwt";
import { getDefaultHomePath } from "./lib/post-login";

const ACCESS_COOKIE = "access_token";

/** Chemins protégés : auth + RBAC (PRD §9) */
function hasRoleAccess(pathname: string, role: string): boolean {
  const isAdmin = role === "ADMIN";

  if (pathname.startsWith("/finance")) {
    return isAdmin || role === "FINANCIER";
  }

  /** Budget : lecture RH + saisie FINANCIER/ADMIN — pas LOGISTIQUE ni EMPLOYE */
  if (pathname.startsWith("/budget") || pathname.startsWith("/dashboard/budget")) {
    return isAdmin || role === "FINANCIER" || role === "RH";
  }

  if (pathname.startsWith("/dashboard/finance")) {
    return isAdmin || role === "FINANCIER";
  }

  if (pathname.startsWith("/dashboard/rh/conges") || pathname.startsWith("/rh/conges")) {
    return true;
  }

  if (pathname.startsWith("/rh/paie") || pathname.startsWith("/dashboard/rh/paie")) {
    return isAdmin || role === "RH" || role === "FINANCIER";
  }

  if (pathname.startsWith("/dashboard/settings")) {
    return true;
  }

  if (pathname.startsWith("/dashboard/rh") || pathname.startsWith("/rh")) {
    return isAdmin || role === "RH";
  }

  if (pathname.startsWith("/inventaire") || pathname.startsWith("/dashboard/inventaire")) {
    return isAdmin || role === "LOGISTIQUE";
  }

  if (pathname.startsWith("/rapports") || pathname.startsWith("/dashboard/rapports")) {
    return isAdmin || ["FINANCIER", "RH", "LOGISTIQUE"].includes(role);
  }

  if (pathname.startsWith("/dashboard/admin")) {
    return isAdmin;
  }

  /* PRD §9 : EMPLOYE — pas de dashboard KPI ; uniquement paramètres sous /dashboard */
  if (pathname.startsWith("/dashboard")) {
    if (role === "EMPLOYE") {
      return pathname.startsWith("/dashboard/settings");
    }
    return true;
  }

  return false;
}

function isProtectedPath(pathname: string): boolean {
  if (pathname.startsWith("/dashboard")) return true;
  if (pathname === "/finance" || pathname.startsWith("/finance/")) return true;
  if (pathname === "/rh" || pathname.startsWith("/rh/")) return true;
  if (pathname === "/budget" || pathname.startsWith("/budget/")) return true;
  if (pathname === "/inventaire" || pathname.startsWith("/inventaire/")) return true;
  if (pathname === "/rapports" || pathname.startsWith("/rapports/")) return true;
  return false;
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (!isProtectedPath(pathname)) {
    return NextResponse.next();
  }

  const token = request.cookies.get(ACCESS_COOKIE)?.value;
  if (!token) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  const payload = decodeJwtPayload(decodeURIComponent(token));
  if (!payload?.role) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  if (payload.exp && payload.exp * 1000 < Date.now()) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  const role = payload.role;

  if (!hasRoleAccess(pathname, role)) {
    return NextResponse.redirect(new URL(getDefaultHomePath(role), request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/finance",
    "/finance/:path*",
    "/rh",
    "/rh/:path*",
    "/budget",
    "/budget/:path*",
    "/inventaire",
    "/inventaire/:path*",
    "/rapports",
    "/rapports/:path*",
  ],
};
