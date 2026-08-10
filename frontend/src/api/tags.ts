import { apiDelete, apiGet, apiPatch, apiPost } from "./client";
import { Page } from "./notifications";

/** Backend'in TagResponse DTO'suyla ayni sekil. */
export interface Tag {
  id: number;
  name: string;
}

/** Backend'in KolonUsageResponse DTO'suyla ayni sekil — bir tag'i kullanan tek bir kolon. */
export interface ColumnUsage {
  tableId: number;
  tableName: string;
  schemaName: string;
  columnId: number;
  columnName: string;
}

/**
 * TagController'daki uc endpoint'in (list/create/usage) frontend karsiligi. GET /api/tags artik
 * sayfalanmis donuyor (bkz. TagController#list) ama "Tagler" gorunumu tum etiketleri tek dizide
 * bekliyor — schemas.ts'teki Page<T> desenindeki gibi size=1000 ile tek istekte hepsini cekip
 * content'i acariyoruz.
 */
export function getTags(): Promise<Tag[]> {
  return apiGet<Page<Tag>>("/api/tags?size=1000").then((page) => page.content);
}

export function createTag(name: string): Promise<Tag> {
  return apiPost<Tag>("/api/tags", { name });
}

/** Bu tag'i tasiyan tum kolonlari, tablo/schema bilgisiyle birlikte doner ("Tagler" gorunumundeki ayrinti butonu icin). */
export function getTagUsage(tagId: number): Promise<ColumnUsage[]> {
  return apiGet<ColumnUsage[]>(`/api/tags/${tagId}/columns`);
}

/** PATCH /api/tags/{id} — sadece ismi degistirir. */
export function renameTag(id: number, name: string): Promise<Tag> {
  return apiPatch<Tag>(`/api/tags/${id}`, { name });
}

/** DELETE /api/tags/{id} — tag'i siler; tasiyan columns silinmez, sadece etiketsiz kalir. */
export function deleteTag(id: number): Promise<void> {
  return apiDelete(`/api/tags/${id}`);
}
