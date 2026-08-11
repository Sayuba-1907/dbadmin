import { API_BASE_URL, apiGet, apiPatch, apiPost, getAuthToken } from "./client";

/** Backend'in izin verdigi sayfa boyutlari (bkz. TableDataService#ALLOWED_PAGE_SIZES) — kullaniciya rastgele buyuk bir LIMIT verilmesin diye whitelist'li. */
export const TABLE_DATA_PAGE_SIZES = [20, 50, 100, 200, 500] as const;
export type TableDataPageSize = (typeof TABLE_DATA_PAGE_SIZES)[number];

/** Backend'in {@code TableDataResponse} DTO'suyla ayni sekil. */
export interface TableDataResult {
  columns: string[];
  rows: Record<string, unknown>[];
  totalRows: number;
}

/**
 * {@code GET /api/tables/{id}/data} — metadata degil, gercek Postgres tablosunun satirlarini
 * sayfalanmis olarak ceker (requirement notu 7, "DBeaver'daki gibi Show Data").
 */
export function getTableData(
  tableId: number,
  page: number,
  size: TableDataPageSize
): Promise<TableDataResult> {
  return apiGet<TableDataResult>(`/api/tables/${tableId}/data?page=${page}&size=${size}`);
}

/**
 * {@code POST /api/tables/{id}/data} — kullanicinin kendi satirini eklemesi. Sadece dolu
 * birakilan alanlar gonderilir (bkz. TableDetail#handleInsertRowSubmit) — bos birakilan bir
 * alan "bu kolona dokunma" anlamina gelir, DB'nin varsayilanina/NULL'a duser.
 */
export function insertTableRow(tableId: number, values: Record<string, unknown>): Promise<void> {
  return apiPost<void>(`/api/tables/${tableId}/data`, { values });
}

/**
 * {@code PATCH /api/tables/{id}/data} — var olan bir satirin duzenlenmesi. {@code pk} tablonun
 * PRIMARY KEY kolonlarinin MEVCUT degerlerini icermeli (satiri bulmak icin), {@code values}
 * degistirilecek kolonlardir — PK kolonlari values icinde OLAMAZ (backend 400 doner).
 */
export function updateTableRow(
  tableId: number,
  pk: Record<string, unknown>,
  values: Record<string, unknown>
): Promise<void> {
  return apiPatch<void>(`/api/tables/${tableId}/data`, { pk, values });
}

/**
 * {@code GET /api/tables/{id}/data/csv-export} — requirement notu 8 ("CSV Export ekle ->
 * minio'ya yazilacak"). Yanit dosya govdesi (blob), JSON degil; {@code auditLogs.ts}'teki
 * {@code downloadBackupFile} ile ayni desen: ham fetch + gecici {@code <a download>} elemani.
 */
export async function exportTableDataCsv(tableId: number, fileName: string): Promise<void> {
  const token = getAuthToken();
  const response = await fetch(`${API_BASE_URL}/api/tables/${tableId}/data/csv-export`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error(`CSV export basarisiz: ${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
