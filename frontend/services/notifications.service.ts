import { get, put } from "@/lib/api";
import type { Notification } from "@/lib/types/notifications";

export function listMyNotifications(params?: { nonLuesSeulement?: boolean; page?: number; size?: number }) {
  const q = new URLSearchParams();
  if (params?.nonLuesSeulement != null) q.set("nonLuesSeulement", String(params.nonLuesSeulement));
  if (params?.page != null) q.set("page", String(params.page));
  if (params?.size != null) q.set("size", String(params.size));
  const qs = q.toString();
  return get<{
    content: Notification[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  }>(`notifications${qs ? `?${qs}` : ""}`);
}

export function markNotificationRead(id: string) {
  return put<void>(`notifications/${id}/lu`);
}

export function markAllNotificationsRead() {
  return put<void>("notifications/tout-lu");
}

export function countUnreadNotifications() {
  return get<number>("notifications/nb-non-lues");
}

