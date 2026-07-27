import { apiDelete, apiGet, apiPatch, apiPost } from "./client";

/**
 * Backend'in whitelist'iyle (ColumnType enum) ayni deger seti. `as const` + `typeof [number]`
 * kalibiyla hem calisma zamaninda kullanilabilecek bir dizi hem de derleme zamaninda
 * "numeric" | "text" | "datetime" | "boolean" birlesim tipi (union type) elde ediyoruz.
 */
export const KOLON_TYPES = ["numeric", "text", "datetime", "boolean"] as const;
export type KolonType = (typeof KOLON_TYPES)[number];

/** Backend'in KolonResponse DTO'suyla ayni sekil. */
export interface Kolon {
  id: number;
  name: string;
  type: string;
  tagId: number | null;
  tagName: string | null;
  primaryKey: boolean;
}

/** Backend'in TabloResponse DTO'suyla ayni sekil. */
export interface Tablo {
  id: number;
  name: string;
  kolonlar: Kolon[];
}

/** Tablo olustururken/kolon eklerken forma girilen kolon bilgisi (henuz id/tagName yok). */
export interface CreateKolonInput {
  name: string;
  type: KolonType;
  primaryKey?: boolean;
}

/** Bu dosyadaki her fonksiyon, TabloController'daki bir endpoint'in birebir karsiligidir. */
export function getTablolar(): Promise<Tablo[]> {
  return apiGet<Tablo[]>("/api/tablolar");
}

export function createTablo(name: string, kolonlar: CreateKolonInput[]): Promise<Tablo> {
  return apiPost<Tablo>("/api/tablolar", { name, kolonlar });
}

export function deleteTablo(id: number): Promise<void> {
  return apiDelete(`/api/tablolar/${id}`);
}

export function addKolon(tabloId: number, kolon: CreateKolonInput): Promise<Kolon> {
  return apiPost<Kolon>(`/api/tablolar/${tabloId}/kolonlar`, kolon);
}

export function deleteKolon(tabloId: number, kolonId: number): Promise<void> {
  return apiDelete(`/api/tablolar/${tabloId}/kolonlar/${kolonId}`);
}

export function renameTablo(id: number, name: string): Promise<Tablo> {
  return apiPatch<Tablo>(`/api/tablolar/${id}`, { name });
}

export function renameKolon(tabloId: number, kolonId: number, name: string): Promise<Kolon> {
  return apiPatch<Kolon>(`/api/tablolar/${tabloId}/kolonlar/${kolonId}/name`, { name });
}

export function changeKolonTag(
  tabloId: number,
  kolonId: number,
  tagId: number | null
): Promise<Kolon> {
  return apiPatch<Kolon>(`/api/tablolar/${tabloId}/kolonlar/${kolonId}/tag`, { tagId });
}
