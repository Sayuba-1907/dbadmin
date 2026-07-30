import { apiDelete, apiGet, apiPatch, apiPost } from "./client";

/** Backend'in SchemaResponse DTO'suyla ayni sekil. */
export interface Schema {
  id: number;
  name: string;
  tabloSayisi: number;
}

/** Backend'in TabloSummaryResponse DTO'suyla ayni sekil — sadece id/name/kolonSayisi, kolonlarin kendisi yok. */
export interface TabloSummary {
  id: number;
  name: string;
  kolonSayisi: number;
}

/** Backend'in TableSummaryDTO'suyla ayni sekil — GET /api/schemalar/schemaList'in ic ice donen tablo satiri. */
interface WorkspaceTabloSummary {
  id: number;
  name: string;
  columnCount: number;
  schemaId: number;
}

/** Backend'in SchemaResponseDTO'suyla ayni sekil. */
interface WorkspaceSchema {
  schemaId: number;
  schemaName: string;
  tableResponseList: WorkspaceTabloSummary[];
}

/** SchemaController'daki endpoint'lerin frontend karsiligi. */
export function getSchemalar(): Promise<Schema[]> {
  return apiGet<Schema[]>("/api/schemalar");
}

/** GET /api/schemalar/{id}/tablolar — bir schema'nin altindaki tablolarin ozet listesi (sidebar icin). */
export function getSchemaTablolar(schemaId: number): Promise<TabloSummary[]> {
  return apiGet<TabloSummary[]>(`/api/schemalar/${schemaId}/tablolar`);
}

/**
 * GET /api/schemalar/schemaList — TUM schema'lari ve altlarindaki tablo ozetlerini TEK istekte
 * getirir (workspace ekraninin ilk yuklemesi ve tablo/schema mutasyonlari sonrasi tazeleme icin).
 * Eskiden bu veri "schema listesi + her schema icin ayri istek" (N+1) seklinde cekiliyordu; bkz.
 * backend'deki TabloRepository.findAllSchemaTabloPairs/countKolonlarGroupByTablo.
 */
export function getWorkspace(): Promise<WorkspaceSchema[]> {
  return apiGet<WorkspaceSchema[]>("/api/schemalar/schemaList");
}

export function createSchema(name: string): Promise<Schema> {
  return apiPost<Schema>("/api/schemalar", { name });
}

export function deleteSchema(id: number): Promise<void> {
  return apiDelete(`/api/schemalar/${id}`);
}

export function renameSchema(id: number, name: string): Promise<Schema> {
  return apiPatch<Schema>(`/api/schemalar/${id}`, { name });
}
