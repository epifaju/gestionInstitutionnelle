import { useEffect, useMemo, useState } from "react";
import type { IMessage } from "@stomp/stompjs";

import { useAuthStore } from "@/lib/store";
import { WebSocketClient } from "@/lib/websocket";
import type { Notification } from "@/lib/types/notifications";
import {
  countUnreadNotifications,
  listMyNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/services/notifications.service";

function parseNotif(body: string): Notification | null {
  try {
    return JSON.parse(body) as Notification;
  } catch {
    return null;
  }
}

export function useNotifications() {
  const token = useAuthStore((s) => s.accessToken);
  const user = useAuthStore((s) => s.user);
  const userId = user?.id;
  const orgId = user?.organisationId;

  const ws = useMemo(() => new WebSocketClient(), []);

  const [items, setItems] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);

  async function fetchHistory() {
    const page = await listMyNotifications({ page: 0, size: 10 });
    setItems(page.content ?? []);
    const nb = await countUnreadNotifications();
    setUnreadCount(nb ?? 0);
  }

  async function markAsRead(id: string) {
    await markNotificationRead(id);
    setItems((prev) => prev.map((n) => (n.id === id ? { ...n, lu: true } : n)));
    setUnreadCount((c) => Math.max(0, c - 1));
  }

  async function markAllRead() {
    await markAllNotificationsRead();
    setItems((prev) => prev.map((n) => ({ ...n, lu: true })));
    setUnreadCount(0);
  }

  useEffect(() => {
    if (!token || !userId || !orgId) return;

    ws.connect(token);
    ws.subscribe(`/queue/notifications/${userId}`, (msg: IMessage) => {
      const n = parseNotif(msg.body);
      if (!n) return;
      setItems((prev) => [n, ...prev].slice(0, 10));
      if (!n.lu) setUnreadCount((c) => c + 1);
    });
    ws.subscribe(`/topic/org/${orgId}/notifications`, (msg: IMessage) => {
      const n = parseNotif(msg.body);
      if (!n) return;
      setItems((prev) => [n, ...prev].slice(0, 10));
      if (!n.lu) setUnreadCount((c) => c + 1);
    });

    fetchHistory().catch(() => {
      /* ignore */
    });

    return () => ws.disconnect();
  }, [token, userId, orgId, ws]);

  return { items, unreadCount, fetchHistory, markAsRead, markAllRead };
}

