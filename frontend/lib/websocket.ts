import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";

type Callback = (msg: IMessage) => void;
type PendingSub = { topic: string; callback: Callback };

export class WebSocketClient {
  private client: Client | null = null;
  private subs: StompSubscription[] = [];
  private pending: PendingSub[] = [];
  private retry = 0;
  private readonly maxRetry = 3;

  connect(token: string) {
    if (this.client?.active) return;

    const client = new Client({
      webSocketFactory: () => new SockJS("/ws"),
      connectHeaders: { Authorization: `Bearer ${token}` },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 0, // we handle retries ourselves
      debug: () => {
        /* silent */
      },
      onConnect: () => {
        this.retry = 0;
        this.flushPending();
      },
      onStompError: () => {
        // let it drop; reconnect is handled by onWebSocketClose
      },
      onWebSocketClose: () => {
        if (this.retry >= this.maxRetry) return;
        const wait = Math.min(30000, 1000 * 2 ** this.retry);
        this.retry += 1;
        setTimeout(() => {
          try {
            client.activate();
          } catch {
            /* ignore */
          }
        }, wait);
      },
    });

    this.client = client;
    client.activate();
  }

  subscribe(topic: string, callback: Callback) {
    // The STOMP client throws if we subscribe before connection is established.
    // Keep subscriptions pending until onConnect.
    this.pending.push({ topic, callback });
    this.flushPending();
  }

  disconnect() {
    const c = this.client;
    this.client = null;
    for (const s of this.subs) {
      try {
        s.unsubscribe();
      } catch {
        /* ignore */
      }
    }
    this.subs = [];
    this.pending = [];
    if (c) {
      try {
        c.deactivate();
      } catch {
        /* ignore */
      }
    }
  }

  private flushPending() {
    const c = this.client;
    if (!c || !c.connected) return;
    if (this.pending.length === 0) return;
    const pending = this.pending;
    this.pending = [];
    for (const p of pending) {
      const sub = c.subscribe(p.topic, p.callback);
      this.subs.push(sub);
    }
  }
}

