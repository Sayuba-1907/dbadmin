import { useEffect, useRef } from "react";
import { API_BASE_URL, getAuthToken } from "../api/client";

const INITIAL_DELAY_MS = 1000;
const MAX_DELAY_MS = 30000;

/**
 * General-purpose WebSocket connection hook — see requirement-websocket-notifications.md Phase 5
 * Step 5.2. Its only current consumer is {@link useNotifications}, but it is deliberately kept
 * separate from business logic (parsing a notification / updating the count): this hook's only
 * responsibility is "connect, notify when a message arrives, retry if disconnected."
 * <p>
 * The JWT is carried as a query param ({@code ?token=...}), the way the backend's
 * {@code NotificationWebSocketHandler} expects it — because the browser's native WebSocket API
 * cannot attach a custom {@code Authorization} header to the handshake request (see the matching
 * rationale on the backend).
 * <p>
 * Reconnection: on ANY close (server restarted, network dropped, etc.) a retry is scheduled with a
 * delay starting at 1s and doubling (capped at 30s) — recovers within a reasonable time without
 * flooding the server with a request storm. A successful connection resets the delay.
 */
export function useWebSocket(path: string, onMessage: (data: string) => void, enabled: boolean) {
  // Ref so the latest onMessage is used without a closure: connection setup should only re-run
  // when path/enabled change, not close and reopen the socket just because onMessage got a new
  // function reference on every render.
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let closed = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: number | null = null;
    let delay = INITIAL_DELAY_MS;

    function connect() {
      const token = getAuthToken();
      if (!token) {
        return;
      }
      const wsUrl =
        API_BASE_URL.replace(/^http/, "ws") + path + "?token=" + encodeURIComponent(token);
      socket = new WebSocket(wsUrl);

      socket.onopen = () => {
        delay = INITIAL_DELAY_MS;
      };
      socket.onmessage = (event) => onMessageRef.current(event.data);
      socket.onclose = () => {
        if (closed) {
          return;
        }
        reconnectTimer = window.setTimeout(connect, delay);
        delay = Math.min(delay * 2, MAX_DELAY_MS);
      };
    }

    connect();

    return () => {
      closed = true;
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
      }
      socket?.close();
    };
  }, [path, enabled]);
}
