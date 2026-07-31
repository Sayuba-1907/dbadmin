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
  schemaId: number;
  schemaName: string;
  kolonlar: Kolon[];
  /** ISO 8601 zaman damgasi (backend'in Instant'i JSON'da boyle serialize olur). Eski satirlarda null olabilir. */
  updatedAt: string | null;
}

/** Tablo olustururken/kolon eklerken forma girilen kolon bilgisi (henuz id/tagName yok). */
export interface CreateKolonInput {
  name: string;
  type: KolonType;
  tagId?: number | null;
  primaryKey?: boolean;
}

/**
 * "Tablo duzenleme oturumu" icin taslak (draft) modeli — TabloDetail'de yapilan her degisiklik
 * (isim, kolon ekle/sil/adlandir/tag/PK) API'ye aninda gitmez, bu yerel yapiyi gunceller.
 * Kaydet'e basilinca bu taslak ile orijinal {@link Tablo} karsilastirilip fark (diff) hesaplanir
 * ve tek bir {@link applyTabloChanges} cagrisi ile gonderilir.
 */
export interface DraftKolon {
  /** Var olan bir kolon icin gercek id; henuz kaydedilmemis (yeni eklenen) bir kolon icin negatif gecici id. */
  id: number;
  /** true ise bu kolon henuz backend'de yok — Kaydet'te "eklenecekKolonlar"a gider. */
  isNew: boolean;
  name: string;
  type: KolonType;
  tagId: number | null;
  primaryKey: boolean;
  /** Var olan bir kolon icin "Kaydet'te silinecek" isareti — Kaydet'e kadar geri alinabilir. */
  silinecek: boolean;
}

export interface TabloDraft {
  tabloId: number;
  name: string;
  schemaId: number;
  kolonlar: DraftKolon[];
}

/** Bir {@link Tablo}'dan yeni bir duzenleme taslagi kurar — hicbir kolon "yeni" ya da "silinecek" degildir. */
export function buildTabloDraft(tablo: Tablo): TabloDraft {
  return {
    tabloId: tablo.id,
    name: tablo.name,
    schemaId: tablo.schemaId,
    kolonlar: tablo.kolonlar.map((kolon) => ({
      id: kolon.id,
      isNew: false,
      name: kolon.name,
      type: kolon.type as KolonType,
      tagId: kolon.tagId,
      primaryKey: kolon.primaryKey,
      silinecek: false,
    })),
  };
}

/** Bu dosyadaki her fonksiyon, TabloController'daki bir endpoint'in birebir karsiligidir. */
/** GET /api/tablolar/{id} — tek bir tablonun kolonlar dahil tam detayi. Sidebar'da bir tabloya tiklaninca cagrilir. */
export function getTablo(id: number): Promise<Tablo> {
  return apiGet<Tablo>(`/api/tablolar/${id}`);
}

export function createTablo(
  name: string,
  kolonlar: CreateKolonInput[],
  schemaId?: number | null
): Promise<Tablo> {
  return apiPost<Tablo>("/api/tablolar", { name, schemaId: schemaId ?? null, kolonlar });
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

export function changeTabloSchema(id: number, schemaId: number): Promise<Tablo> {
  return apiPatch<Tablo>(`/api/tablolar/${id}/schema`, { schemaId });
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

/**
 * Var olan bir kolonu tablonun PRIMARY KEY'ine ekler/cikarir. Backend her cagrida gercek
 * PRIMARY KEY constraint'ini guncel isaretli kolon setiyle yeniden kurar (birden fazla isaretli
 * kolon varsa composite PK). Tabloda veri varsa ve kolonda NULL/tekrar eden deger bulunuyorsa
 * istek hata doner, isaret degismez.
 */
export function changeKolonPrimaryKey(
  tabloId: number,
  kolonId: number,
  primaryKey: boolean
): Promise<Kolon> {
  return apiPatch<Kolon>(`/api/tablolar/${tabloId}/kolonlar/${kolonId}/primary-key`, {
    primaryKey,
  });
}

/** Backend'in KolonGuncellemeRequest DTO'suyla ayni sekil — var olan bir kolonun nihai hali. */
export interface KolonGuncelleme {
  kolonId: number;
  yeniIsim: string;
  yeniTagId: number | null;
  yeniPrimaryKey: boolean;
}

/**
 * Backend'in TabloUpdateRequest DTO'suyla ayni sekil. {@code yeniIsim}/{@code yeniSchemaId}
 * sparse'dir (degismediyse null); {@code guncellenecekKolonlar}'daki her satir ise degisen
 * kolonun TAM nihai halini tasir (bkz. backend'deki ayni isimli DTO'nun aciklamasi).
 */
export interface TabloUpdateRequest {
  yeniIsim: string | null;
  yeniSchemaId: number | null;
  silinecekKolonIdler: number[];
  eklenecekKolonlar: CreateKolonInput[];
  guncellenecekKolonlar: KolonGuncelleme[];
}

/**
 * "Kaydet'e basinca hepsi birden gitsin" akisinin tek cagrisi: biriktirilmis tum degisiklikleri
 * TEK istekte gonderir. Backend bunu tek transaction'da uygular — bir alt-islem patlarsa
 * hicbiri kalici olmaz (bkz. TabloService.applyChanges javadoc'u).
 */
export function applyTabloChanges(id: number, request: TabloUpdateRequest): Promise<Tablo> {
  return apiPatch<Tablo>(`/api/tablolar/${id}/degisiklikler`, request);
}
