import { apiGet, apiPost } from "./client";
import { Page } from "./notifications";

/** Backend's OperationType enum, same values (bkz. entity/OperationType.java). */
export type AuditOperationType =
  | "TABLE_CREATED"
  | "TABLE_DELETED"
  | "TABLE_RENAMED"
  | "TABLE_SCHEMA_CHANGED"
  | "TABLE_UPDATED"
  | "COLUMN_ADDED"
  | "COLUMN_DELETED"
  | "COLUMN_RENAMED"
  | "COLUMN_PRIMARY_KEY_CHANGED"
  | "COLUMN_TAG_CHANGED"
  | "SCHEMA_CREATED"
  | "SCHEMA_RENAMED"
  | "SCHEMA_DELETED"
  | "TAG_CREATED"
  | "TAG_RENAMED"
  | "TAG_DELETED"
  | "USER_CREATED"
  | "USER_ROLE_CHANGED"
  | "USER_DELETED";

/** Backend's TargetType enum, same values. */
export type AuditTargetType = "TABLE" | "COLUMN" | "SCHEMA" | "TAG" | "USER";

/** Backend's AuditLogResponse DTO, same shape. */
export interface AuditLog {
  id: number;
  userId: number | null;
  username: string;
  operationType: AuditOperationType;
  targetType: AuditTargetType;
  targetId: number;
  detail: string | null;
  traceId: string | null;
  createdAt: string;
}

/** Hepsi opsiyonel — GET /api/audit-logs'un query parametreleriyle birebir ayni (bkz. AuditLogController#search). */
export interface AuditLogFilters {
  userId?: number;
  targetType?: AuditTargetType;
  targetId?: number;
  /** ISO instant (ör. "2026-08-01T00:00:00Z"). */
  from?: string;
  /** ISO instant. */
  to?: string;
}

export const AUDIT_LOG_PAGE_SIZE = 20;

/**
 * {@code GET /api/audit-logs} — Req-2.3 (requirement-maintenance-audit-backup.md): backend'de
 * yeni bir uc yok, mevcut filtre+sayfalama uc'u dogrudan cagriliyor. En yeni once (createdAt desc).
 */
export function getAuditLogs(filters: AuditLogFilters, page: number): Promise<Page<AuditLog>> {
  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("size", String(AUDIT_LOG_PAGE_SIZE));
  params.set("sort", "createdAt,desc");
  if (filters.userId != null) {
    params.set("userId", String(filters.userId));
  }
  if (filters.targetType) {
    params.set("targetType", filters.targetType);
  }
  if (filters.targetId != null) {
    params.set("targetId", String(filters.targetId));
  }
  if (filters.from) {
    params.set("from", filters.from);
  }
  if (filters.to) {
    params.set("to", filters.to);
  }
  return apiGet<Page<AuditLog>>(`/api/audit-logs?${params.toString()}`);
}

/** Backend's AuditLogBackupResponse DTO, same shape. */
export interface AuditLogBackupResult {
  key: string;
  rowCount: number;
  backedUpAt: string;
}

/** {@code POST /api/maintenance/audit-logs/backup} (Req-2.4) — govde gerektirmiyor. */
export function backupAuditLogs(): Promise<AuditLogBackupResult> {
  return apiPost<AuditLogBackupResult>("/api/maintenance/audit-logs/backup", {});
}

/** Backend's AuditLogBackupListItemDto, same shape. */
export interface AuditLogBackupListItem {
  key: string;
  backedUpBy: string;
  backedUpAt: string;
  rowCount: number;
}

/** {@code GET /api/maintenance/audit-logs/backups} (Req-2.6) — MinIO'daki gecmis yedeklerin listesi, sadece goruntuleme. */
export function getBackupList(): Promise<AuditLogBackupListItem[]> {
  return apiGet<AuditLogBackupListItem[]>("/api/maintenance/audit-logs/backups");
}
