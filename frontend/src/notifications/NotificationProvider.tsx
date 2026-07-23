import {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import { ApiError } from "../api/client";
import "./notifications.css";

type NotificationKind = "success" | "conflict" | "error";

interface Notification {
  kind: NotificationKind;
  message: string;
}

type NotifyFn = (status: number, message: string) => void;

const NotificationContext = createContext<NotifyFn | null>(null);

function kindForStatus(status: number): NotificationKind {
  if (status < 300) {
    return "success";
  }
  if (status === 409) {
    return "conflict";
  }
  return "error";
}

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notification, setNotification] = useState<Notification | null>(null);

  const notify = useCallback<NotifyFn>((status, message) => {
    setNotification({ kind: kindForStatus(status), message });
  }, []);

  useEffect(() => {
    if (!notification) {
      return;
    }
    const timer = setTimeout(() => setNotification(null), 4000);
    return () => clearTimeout(timer);
  }, [notification]);

  return (
    <NotificationContext.Provider value={notify}>
      {children}
      {notification && (
        <div className={`notification notification-${notification.kind}`} role="alert">
          {notification.message}
        </div>
      )}
    </NotificationContext.Provider>
  );
}

export function useNotify(): NotifyFn {
  const notify = useContext(NotificationContext);
  if (!notify) {
    throw new Error("useNotify must be used within a NotificationProvider");
  }
  return notify;
}

export function notifyFromError(notify: NotifyFn, err: unknown, fallbackMessage: string) {
  if (err instanceof ApiError) {
    notify(err.status, err.message);
    return;
  }
  notify(500, fallbackMessage);
}
