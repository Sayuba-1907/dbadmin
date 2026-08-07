import { useCallback, useEffect, useState } from "react";
import {
  AppNotification,
  getNotifications,
  getUnreadCount,
  markAllNotificationsAsRead,
  markNotificationAsRead,
} from "../api/notifications";
import { useWebSocket } from "./useWebSocket";

/** Same shape as the JSON body sent by NotificationPushListener (see the backend Payload record) — no isRead, a push is ALWAYS a fresh/unread notification. */
type NotificationPushMessage = Omit<AppNotification, "isRead">;

/**
 * Custom hook that separates the notification domain's read+push-listening responsibility from
 * Dashboard — see requirement-websocket-notifications.md Phase 5 Step 5.3, continuing the
 * custom-hook pattern (useTags/useSchemas) approved the previous week.
 * <p>
 * Req-2.5 counterpart: on mount ONLY {@code unread-count} is fetched (automatically, like
 * useSchemas, error not swallowed). The full list ({@link list}) is DELIBERATELY not fetched
 * automatically like useTags — {@link refresh} is called when the user opens the notification
 * panel. After that, both the count AND the list are updated locally ONLY via WebSocket push
 * ({@link useWebSocket}), never re-queried from the server.
 * <p>
 * {@code onNewNotification}: called only on a LIVE push (not for existing notifications in the
 * initial load) — Dashboard uses this to show a popup (Req-2.8). Error handling is the same as
 * the other hooks: thrown, not swallowed.
 * <p>
 * {@code enabled}: App.tsx MUST call this hook even while AuthProvider is in the "loading"/"anonymous"
 * state (React Hooks rule — must be called unconditionally) — but while there is no identity yet,
 * neither the count should be fetched nor should it connect to WebSocket (otherwise it would be a
 * 401 / a pointless connection attempt). While {@code enabled=false} this hook is completely inert.
 */
export function useNotifications(
  enabled: boolean,
  onNewNotification?: (notification: AppNotification) => void
) {
  const [count, setCount] = useState(0);
  const [list, setList] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (enabled) {
      getUnreadCount().then(setCount);
    }
  }, [enabled]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const page = await getNotifications();
      setList(page.content);
    } finally {
      setLoading(false);
    }
  }, []);

  const handlePush = useCallback(
    (data: string) => {
      // Best-effort channel (see backend Req-3.4): a malformed/unexpected frame must not crash
      // the page, it is silently ignored.
      let payload: NotificationPushMessage;
      try {
        payload = JSON.parse(data);
      } catch {
        return;
      }
      const newNotification: AppNotification = { ...payload, isRead: false };
      setCount((c) => c + 1);
      setList((prev) => [newNotification, ...prev]);
      onNewNotification?.(newNotification);
    },
    [onNewNotification]
  );

  useWebSocket("/ws", handlePush, enabled);

  const markAsRead = useCallback(async (id: number) => {
    await markNotificationAsRead(id);
    setList((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
    setCount((c) => Math.max(0, c - 1));
  }, []);

  const markAllAsRead = useCallback(async () => {
    await markAllNotificationsAsRead();
    setList((prev) => prev.map((n) => ({ ...n, isRead: true })));
    setCount(0);
  }, []);

  return { count, list, loading, refresh, markAsRead, markAllAsRead };
}
