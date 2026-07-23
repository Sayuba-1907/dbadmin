import { apiDelete, apiGet, apiPatch, apiPost } from "./client";

export const KOLON_TYPES = ["numeric", "text", "datetime", "boolean"] as const;
export type KolonType = (typeof KOLON_TYPES)[number];

export interface Kolon {
  id: number;
  name: string;
  type: string;
  tagId: number | null;
  tagName: string | null;
}

export interface Tablo {
  id: number;
  name: string;
  kolonlar: Kolon[];
}

export interface CreateKolonInput {
  name: string;
  type: KolonType;
}

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
