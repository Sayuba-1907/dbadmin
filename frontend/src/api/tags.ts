import { apiDelete, apiGet, apiPatch, apiPost } from "./client";

/** Backend'in TagResponse DTO'suyla ayni sekil. */
export interface Tag {
  id: number;
  name: string;
}

/** Backend'in KolonUsageResponse DTO'suyla ayni sekil — bir tag'i kullanan tek bir kolon. */
export interface KolonUsage {
  tabloId: number;
  tabloName: string;
  schemaName: string;
  kolonId: number;
  kolonName: string;
}

/** TagController'daki uc endpoint'in (list/create/usage) frontend karsiligi. */
export function getTags(): Promise<Tag[]> {
  return apiGet<Tag[]>("/api/tags");
}

export function createTag(name: string): Promise<Tag> {
  return apiPost<Tag>("/api/tags", { name });
}

/** Bu tag'i tasiyan tum kolonlari, tablo/schema bilgisiyle birlikte doner ("Tagler" gorunumundeki ayrinti butonu icin). */
export function getTagUsage(tagId: number): Promise<KolonUsage[]> {
  return apiGet<KolonUsage[]>(`/api/tags/${tagId}/kolonlar`);
}

/** PATCH /api/tags/{id} — sadece ismi degistirir. */
export function renameTag(id: number, name: string): Promise<Tag> {
  return apiPatch<Tag>(`/api/tags/${id}`, { name });
}

/** DELETE /api/tags/{id} — tag'i siler; tasiyan kolonlar silinmez, sadece etiketsiz kalir. */
export function deleteTag(id: number): Promise<void> {
  return apiDelete(`/api/tags/${id}`);
}
