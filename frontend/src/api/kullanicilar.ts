import { apiDelete, apiGet, apiPatch, apiPost } from "./client";
import { Rol } from "./auth";

/** Backend'in {@code KullaniciResponse} DTO'suyla ayni sekil — parola hash'i hic gelmez. */
export interface Kullanici {
  id: number;
  kullaniciAdi: string;
  rol: Rol;
}

/** {@code /api/kullanicilar} altindaki uc uc — hepsi sadece ADMIN icin acik (bkz. SecurityConfig). */
export function getKullanicilar(): Promise<Kullanici[]> {
  return apiGet<Kullanici[]>("/api/kullanicilar");
}

/** {@code rol} verilmezse backend VIEWER atar. */
export function createKullanici(
  kullaniciAdi: string,
  parola: string,
  rol: Rol
): Promise<Kullanici> {
  return apiPost<Kullanici>("/api/kullanicilar", { kullaniciAdi, parola, rol });
}

export function changeKullaniciRol(id: number, rol: Rol): Promise<Kullanici> {
  return apiPatch<Kullanici>(`/api/kullanicilar/${id}/rol`, { rol });
}

export function deleteKullanici(id: number): Promise<void> {
  return apiDelete(`/api/kullanicilar/${id}`);
}
