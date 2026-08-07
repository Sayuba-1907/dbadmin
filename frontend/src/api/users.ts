import { apiDelete, apiGet, apiPatch, apiPost } from "./client";
import { Role } from "./auth";

/** Backend'in {@code KullaniciResponse} DTO'suyla ayni sekil — parola hash'i hic gelmez. */
export interface User {
  id: number;
  username: string;
  role: Role;
}

/** {@code /api/users} altindaki uc uc — hepsi sadece ADMIN icin acik (bkz. SecurityConfig). */
export function getUsers(): Promise<User[]> {
  return apiGet<User[]>("/api/users");
}

/** {@code role} verilmezse backend VIEWER atar. */
export function createUser(username: string, password: string, role: Role): Promise<User> {
  return apiPost<User>("/api/users", { username, password, role });
}

export function changeUserRole(id: number, role: Role): Promise<User> {
  return apiPatch<User>(`/api/users/${id}/role`, { role });
}

export function deleteUser(id: number): Promise<void> {
  return apiDelete(`/api/users/${id}`);
}
