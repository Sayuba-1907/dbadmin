import { apiDelete, apiGet, apiPatch, apiPost } from "./client";

/** Backend'in SchemaResponse DTO'suyla ayni sekil. */
export interface Schema {
  id: number;
  name: string;
  tableCount: number;
}

/** Backend'in TabloSummaryResponse DTO'suyla ayni sekil — sadece id/name/columnCount, kolonlarin kendisi yok. */
export interface TableSummary {
  id: number;
  name: string;
  columnCount: number;
}

/** Backend'in TableSummaryDTO'suyla ayni sekil — GET /api/schemas/schemaList'in ic ice donen tablo satiri. */
interface WorkspaceTableSummary {
  id: number;
  name: string;
  columnCount: number;
  schemaId: number;
}

/** Backend'in SchemaResponseDTO'suyla ayni sekil. */
interface WorkspaceSchema {
  schemaId: number;
  schemaName: string;
  tableResponseList: WorkspaceTableSummary[];
}

/** SchemaController'daki endpoint'lerin frontend karsiligi. */
export function getSchemas(): Promise<Schema[]> {
  return apiGet<Schema[]>("/api/schemas");
}

/** GET /api/schemas/{id}/tables — bir schema'nin altindaki tablolarin ozet listesi (sidebar icin). */
export function getSchemaTables(schemaId: number): Promise<TableSummary[]> {
  return apiGet<TableSummary[]>(`/api/schemas/${schemaId}/tables`);
}

/**
 * GET /api/schemas/schemaList — TUM schema'lari ve altlarindaki tablo ozetlerini TEK istekte
 * getirir (workspace ekraninin ilk yuklemesi ve tablo/schema mutasyonlari sonrasi tazeleme icin).
 * Eskiden bu veri "schema listesi + her schema icin ayri istek" (N+1) seklinde cekiliyordu; bkz.
 * backend'deki TabloRepository.findAllSchemaTabloPairs/countKolonlarGroupByTablo.
 */
export function getWorkspace(): Promise<WorkspaceSchema[]> {
  return apiGet<WorkspaceSchema[]>("/api/schemas/schemaList");
}

export function createSchema(name: string): Promise<Schema> {
  return apiPost<Schema>("/api/schemas", { name });
}

export function deleteSchema(id: number): Promise<void> {
  return apiDelete(`/api/schemas/${id}`);
}

export function renameSchema(id: number, name: string): Promise<Schema> {
  return apiPatch<Schema>(`/api/schemas/${id}`, { name });
}
