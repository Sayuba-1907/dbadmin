import { apiGet, apiPost } from "./client";

/** Backend'in {@code Role} enum'uyla birebir ayni degerler. */
export type Role = "VIEWER" | "EDITOR" | "ADMIN";

/** Backend'in {@code LoginResponse} DTO'suyla ayni sekil. */
export interface LoginResult {
  token: string;
  username: string;
  role: Role;
}

export function login(username: string, password: string): Promise<LoginResult> {
  return apiPost<LoginResult>("/api/auth/login", { username, password });
}

/** Sayfa yenilendiginde localStorage'daki token'in hala gecerli olup olmadigini dogrulamak icin. */
export function me(): Promise<LoginResult> {
  return apiGet<LoginResult>("/api/auth/me");
}
