import { apiGet, apiPatch } from "./client";

/** Backend's NotificationType enum, same values. */
export type NotificationEventType =
  | "TABLE_RENAMED"
  | "TABLE_SCHEMA_CHANGED"
  | "TABLE_DELETED"
  | "TABLE_UPDATED"
  | "COLUMN_ADDED"
  | "COLUMN_DELETED"
  | "COLUMN_RENAMED"
  | "COLUMN_PRIMARY_KEY_CHANGED"
  | "COLUMN_TAG_CHANGED";

/** Same shape as the backend's NotificationResponse DTO. */
export interface AppNotification {
  id: number;
  tableId: number;
  tableName: string;
  triggeredByUsername: string;
  type: NotificationEventType;
  message: string;
  isRead: boolean;
  createdAt: string;
}

/** Only the fields used from Spring Data's Page<T> JSON — the rest (sort, pageable, ...) is unused here. */
export interface Page<T> {
  content: T[];
  totalElements: number;
}

/** GET /api/notifications — newest first, first 50 notifications (see NotificationController#list). */
export function getNotifications(): Promise<Page<AppNotification>> {
  return apiGet<Page<AppNotification>>("/api/notifications?size=50");
}

/** GET /api/notifications/unread-count — called ONCE on initial page load / first WebSocket connect (Req-2.5). */
export function getUnreadCount(): Promise<number> {
  return apiGet<{ count: number }>("/api/notifications/unread-count").then((r) => r.count);
}

/** PATCH /api/notifications/{id}/read */
export function markNotificationAsRead(id: number): Promise<void> {
  return apiPatch<void>(`/api/notifications/${id}/read`, {});
}

/** PATCH /api/notifications/read — mark all as read. */
export function markAllNotificationsAsRead(): Promise<void> {
  return apiPatch<void>("/api/notifications/read", {});
}
