import { apiGet } from "./client";

/** Backend's SystemSummaryResponse DTO, same shape (Req-2.1). */
export interface SystemSummary {
  schemaCount: number;
  tableCount: number;
  columnCount: number;
  userCount: number;
}

/** Backend's ServiceHealthResponse DTO, same shape (Req-2.2) — basit yesil/kirmizi gosterge. */
export interface ServiceHealth {
  postgres: boolean;
  redis: boolean;
  minio: boolean;
  backend: boolean;
}

export function getSystemSummary(): Promise<SystemSummary> {
  return apiGet<SystemSummary>("/api/maintenance/summary");
}

export function getServiceHealth(): Promise<ServiceHealth> {
  return apiGet<ServiceHealth>("/api/maintenance/health");
}
