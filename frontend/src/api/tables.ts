import { apiDelete, apiGet, apiPatch, apiPost } from "./client";

/**
 * Backend'in whitelist'iyle (ColumnType enum) ayni deger seti. `as const` + `typeof [number]`
 * kalibiyla hem calisma zamaninda kullanilabilecek bir dizi hem de derleme zamaninda
 * "numeric" | "text" | "datetime" | "boolean" birlesim tipi (union type) elde ediyoruz.
 */
export const COLUMN_TYPES = ["numeric", "text", "datetime", "boolean"] as const;
export type ColumnType = (typeof COLUMN_TYPES)[number];

/** Backend'in KolonResponse DTO'suyla ayni sekil. */
export interface Column {
  id: number;
  name: string;
  type: string;
  tagId: number | null;
  tagName: string | null;
  primaryKey: boolean;
}

/** Backend'in TabloResponse DTO'suyla ayni sekil. */
export interface Table {
  id: number;
  name: string;
  schemaId: number;
  schemaName: string;
  columns: Column[];
  /** ISO 8601 zaman damgasi (backend'in Instant'i JSON'da boyle serialize olur). Eski satirlarda null olabilir. */
  updatedAt: string | null;
}

/** Table olustururken/kolon eklerken forma girilen kolon bilgisi (henuz id/tagName yok). */
export interface CreateColumnInput {
  name: string;
  type: ColumnType;
  tagId?: number | null;
  primaryKey?: boolean;
}

/**
 * "Table duzenleme oturumu" icin taslak (draft) modeli — TableDetail'de yapilan her degisiklik
 * (isim, kolon ekle/sil/adlandir/tag/PK) API'ye aninda gitmez, bu yerel yapiyi gunceller.
 * Kaydet'e basilinca bu taslak ile orijinal {@link Table} karsilastirilip fark (diff) hesaplanir
 * ve tek bir {@link applyTableChanges} cagrisi ile gonderilir.
 */
export interface DraftColumn {
  /** Var olan bir kolon icin gercek id; henuz kaydedilmemis (yeni eklenen) bir kolon icin negatif gecici id. */
  id: number;
  /** true ise bu kolon henuz backend'de yok — Kaydet'te "columnsToAdd"a gider. */
  isNew: boolean;
  name: string;
  type: ColumnType;
  tagId: number | null;
  primaryKey: boolean;
  /** Var olan bir kolon icin "Kaydet'te toDelete" isareti — Kaydet'e kadar geri alinabilir. */
  toDelete: boolean;
}

export interface TableDraft {
  tableId: number;
  name: string;
  schemaId: number;
  columns: DraftColumn[];
}

/** Bir {@link Table}'dan yeni bir duzenleme taslagi kurar — hicbir kolon "yeni" ya da "toDelete" degildir. */
export function buildTableDraft(table: Table): TableDraft {
  return {
    tableId: table.id,
    name: table.name,
    schemaId: table.schemaId,
    columns: table.columns.map((column) => ({
      id: column.id,
      isNew: false,
      name: column.name,
      type: column.type as ColumnType,
      tagId: column.tagId,
      primaryKey: column.primaryKey,
      toDelete: false,
    })),
  };
}

/** Bu dosyadaki her fonksiyon, TabloController'daki bir endpoint'in birebir karsiligidir. */
/** GET /api/tables/{id} — tek bir tablonun columns dahil tam detayi. Sidebar'da bir tabloya tiklaninca cagrilir. */
export function getTable(id: number): Promise<Table> {
  return apiGet<Table>(`/api/tables/${id}`);
}

export function createTable(
  name: string,
  columns: CreateColumnInput[],
  schemaId?: number | null
): Promise<Table> {
  return apiPost<Table>("/api/tables", { name, schemaId: schemaId ?? null, columns });
}

export function deleteTable(id: number): Promise<void> {
  return apiDelete(`/api/tables/${id}`);
}

export function addColumn(tableId: number, column: CreateColumnInput): Promise<Column> {
  return apiPost<Column>(`/api/tables/${tableId}/columns`, column);
}

export function deleteColumn(tableId: number, columnId: number): Promise<void> {
  return apiDelete(`/api/tables/${tableId}/columns/${columnId}`);
}

export function renameTable(id: number, name: string): Promise<Table> {
  return apiPatch<Table>(`/api/tables/${id}`, { name });
}

export function changeTableSchema(id: number, schemaId: number): Promise<Table> {
  return apiPatch<Table>(`/api/tables/${id}/schema`, { schemaId });
}

export function renameColumn(tableId: number, columnId: number, name: string): Promise<Column> {
  return apiPatch<Column>(`/api/tables/${tableId}/columns/${columnId}/name`, { name });
}

export function changeColumnTag(
  tableId: number,
  columnId: number,
  tagId: number | null
): Promise<Column> {
  return apiPatch<Column>(`/api/tables/${tableId}/columns/${columnId}/tag`, { tagId });
}

/**
 * Var olan bir kolonu tablonun PRIMARY KEY'ine ekler/cikarir. Backend her cagrida gercek
 * PRIMARY KEY constraint'ini guncel isaretli kolon setiyle yeniden kurar (birden fazla isaretli
 * kolon varsa composite PK). Tabloda veri varsa ve kolonda NULL/tekrar eden deger bulunuyorsa
 * istek hata doner, isaret degismez.
 */
export function changeColumnPrimaryKey(
  tableId: number,
  columnId: number,
  primaryKey: boolean
): Promise<Column> {
  return apiPatch<Column>(`/api/tables/${tableId}/columns/${columnId}/primary-key`, {
    primaryKey,
  });
}

/** Backend'in KolonGuncellemeRequest DTO'suyla ayni sekil — var olan bir kolonun nihai hali. */
export interface ColumnUpdate {
  columnId: number;
  newName: string;
  newTagId: number | null;
  newPrimaryKey: boolean;
}

/**
 * Backend'in TableUpdateRequest DTO'suyla ayni sekil. {@code newName}/{@code newSchemaId}
 * sparse'dir (degismediyse null); {@code columnsToUpdate}'daki her satir ise degisen
 * kolonun TAM nihai halini tasir (bkz. backend'deki ayni isimli DTO'nun aciklamasi).
 */
export interface TableUpdateRequest {
  newName: string | null;
  newSchemaId: number | null;
  columnIdsToDelete: number[];
  columnsToAdd: CreateColumnInput[];
  columnsToUpdate: ColumnUpdate[];
}

/**
 * "Kaydet'e basinca hepsi birden gitsin" akisinin tek cagrisi: biriktirilmis tum degisiklikleri
 * TEK istekte gonderir. Backend bunu tek transaction'da uygular — bir alt-islem patlarsa
 * hicbiri kalici olmaz (bkz. TabloService.applyChanges javadoc'u).
 */
export function applyTableChanges(id: number, request: TableUpdateRequest): Promise<Table> {
  return apiPatch<Table>(`/api/tables/${id}/changes`, request);
}
