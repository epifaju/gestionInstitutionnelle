import { DashboardShell } from "@/components/dashboard-shell";

export default function RhSectionLayout({ children }: { children: React.ReactNode }) {
  return <DashboardShell>{children}</DashboardShell>;
}
