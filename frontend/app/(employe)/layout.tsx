"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Palmtree, Wallet, FileText, UserCircle } from "lucide-react";
import { useAuthStore } from "@/lib/store";
import { NotificationBell } from "@/components/ui/NotificationBell";

function tabClass(active: boolean) {
  return `flex flex-1 flex-col items-center justify-center gap-1 py-2 text-xs ${
    active ? "text-indigo-700" : "text-muted-foreground"
  }`;
}

export default function EmployeLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-40 border-b border-border bg-card px-4 py-3 text-card-foreground">
        <div className="flex items-center justify-between">
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">Mon espace</p>
            <p className="truncate text-sm font-semibold text-foreground">
              {user?.prenom ?? ""} {user?.nom ?? ""}
            </p>
          </div>
          <NotificationBell />
        </div>
      </header>

      <main className="px-4 pb-20 pt-4">{children}</main>

      <nav className="fixed bottom-0 left-0 right-0 z-40 border-t border-border bg-card text-card-foreground">
        <div className="mx-auto flex max-w-md">
          <Link href="/employe" className={tabClass(pathname === "/employe")}>
            <Home className="h-5 w-5" />
            Accueil
          </Link>
          <Link href="/employe/conges" className={tabClass(pathname.startsWith("/employe/conges"))}>
            <Palmtree className="h-5 w-5" />
            Congés
          </Link>
          <Link href="/employe/paie" className={tabClass(pathname.startsWith("/employe/paie"))}>
            <Wallet className="h-5 w-5" />
            Paie
          </Link>
          <Link href="/mes-documents" className={tabClass(pathname.startsWith("/mes-documents"))}>
            <FileText className="h-5 w-5" />
            Docs
          </Link>
          <Link href="/employe/profil" className={tabClass(pathname.startsWith("/employe/profil"))}>
            <UserCircle className="h-5 w-5" />
            Profil
          </Link>
        </div>
      </nav>
    </div>
  );
}

