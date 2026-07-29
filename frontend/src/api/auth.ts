import { apiGet, apiPost } from "./client";

/** Backend'in {@code Rol} enum'uyla birebir ayni degerler. */
export type Rol = "VIEWER" | "EDITOR" | "ADMIN";

/** Backend'in {@code LoginResponse} DTO'suyla ayni sekil. */
export interface LoginResult {
  token: string;
  kullaniciAdi: string;
  rol: Rol;
}

export function login(kullaniciAdi: string, parola: string): Promise<LoginResult> {
  return apiPost<LoginResult>("/api/auth/login", { kullaniciAdi, parola });
}

/** Sayfa yenilendiginde localStorage'daki token'in hala gecerli olup olmadigini dogrulamak icin. */
export function ben(): Promise<LoginResult> {
  return apiGet<LoginResult>("/api/auth/ben");
}
