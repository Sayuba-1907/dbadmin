import { apiDelete, apiGet, apiPatch, apiPost } from "./client";
import { Role } from "./auth";
import { Page } from "./notifications";

/** Backend'in {@code KullaniciResponse} DTO'suyla ayni sekil — parola hash'i hic gelmez. */
export interface User {
  id: number;
  username: string;
  role: Role;
}

/**
 * {@code /api/users} altindaki uc uc — hepsi sadece ADMIN icin acik (bkz. SecurityConfig). GET
 * /api/users artik sayfalanmis donuyor (bkz. UserController#list) ama "Kullanicilar" gorunumu
 * tum kullanicilari tek dizide bekliyor — schemas.ts'teki Page<T> desenindeki gibi size=1000 ile
 * tek istekte hepsini cekip content'i acariyoruz.
 */
export function getUsers(): Promise<User[]> {
  return apiGet<Page<User>>("/api/users?size=1000").then((page) => page.content);
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
