import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AppNotification } from "../api/notifications";
import "./NotificationBell.css";

interface NotificationBellProps {
  count: number;
  list: AppNotification[];
  loading: boolean;
  onOpen: () => void;
  onNotificationClick: (notification: AppNotification) => void;
  onMarkAllAsRead: () => void;
}

/**
 * Bell icon + unread count badge + click-to-open notification panel in the top-right corner — see
 * requirement-websocket-notifications.md Phase 5 Step 5.4/5.5. Its own state is only whether the
 * panel is open/closed; the data (count/list) comes entirely as props from {@link
 * useNotifications} (same "state lifting" pattern as the other panels, see Dashboard.tsx's
 * javadoc).
 * <p>
 * The list is DELIBERATELY fetched only on the first open, not every time the panel opens (a
 * continuation of useTags' "no automatic mount-fetch" pattern) — {@code onOpen} is called on every
 * click, but the caller (Dashboard) decides for itself not to re-fetch if it already has.
 */
export function NotificationBell({
  count,
  list,
  loading,
  onOpen,
  onNotificationClick,
  onMarkAllAsRead,
}: NotificationBellProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);

  // Close when clicking outside the panel — standard dropdown behavior.
  useEffect(() => {
    if (!open) {
      return;
    }
    function handleClickOutside(event: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  function handleToggle() {
    const newOpen = !open;
    setOpen(newOpen);
    if (newOpen) {
      onOpen();
    }
  }

  function handleItemClick(notification: AppNotification) {
    onNotificationClick(notification);
    setOpen(false);
  }

  return (
    <div className="notification-bell" ref={panelRef}>
      <button
        type="button"
        className="notification-bell-btn cursor-pointer"
        onClick={handleToggle}
        aria-label={t("notificationBell.buttonLabel")}
        aria-expanded={open}
      >
        <span className="notification-bell-icon" aria-hidden="true">
          🔔
        </span>
        {count > 0 && <span className="notification-bell-badge">{count > 99 ? "99+" : count}</span>}
      </button>

      {open && (
        <div className="notification-panel">
          <div className="notification-panel-header flex align-items-center justify-content-between">
            <span className="notification-panel-title">{t("notificationBell.title")}</span>
            {count > 0 && (
              <button
                type="button"
                className="btn btn-link notification-panel-mark-all cursor-pointer"
                onClick={onMarkAllAsRead}
              >
                {t("notificationBell.markAllRead")}
              </button>
            )}
          </div>
          <div className="notification-panel-list">
            {loading && <div className="notification-panel-empty">{t("dashboard.loading")}</div>}
            {!loading && list.length === 0 && (
              <div className="notification-panel-empty">{t("notificationBell.empty")}</div>
            )}
            {!loading &&
              list.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  className={`notification-item cursor-pointer text-left${notification.isRead ? "" : " unread"}`}
                  onClick={() => handleItemClick(notification)}
                >
                  <span className="notification-item-message">{notification.message}</span>
                  <span className="notification-item-meta">
                    {notification.triggeredByUsername} ·{" "}
                    {new Date(notification.createdAt).toLocaleString()}
                  </span>
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
