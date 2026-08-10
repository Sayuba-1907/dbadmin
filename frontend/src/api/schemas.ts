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

/** Only the fields used from Spring Data's Page<T> JSON — the rest (sort, pageable, ...) is unused here. */
interface Page<T> {
  content: T[];
}

/**
 * SchemaController'daki endpoint'lerin frontend karsiligi. GET /api/schemas artik sayfalanmis
 * donuyor (bkz. SchemaController#list) ama sidebar/schema-secimi TUM schema'lari tek dizide
 * bekliyor — size=1000 ile (Spring'in varsayilan max sayfa boyutu 2000) tek istekte hepsini cekip
 * content'i acariyoruz, notifications.ts'teki Page<T> desenindeki gibi.
 */
export function getSchemas(): Promise<Schema[]> {
  return apiGet<Page<Schema>>("/api/schemas?size=1000").then((page) => page.content);
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
