import { get } from "@/lib/api";
import type { DashboardResponse } from "@/lib/types/dashboard";

export async function getDashboard() {
  return get<DashboardResponse>("rapports/dashboard");
}
