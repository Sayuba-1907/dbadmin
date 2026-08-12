import {
  API_BASE_URL,
  ApiError,
  apiDelete,
  apiGet,
  apiPatch,
  apiPost,
  getAuthToken,
} from "./client";

/** Backend'in {@code Role} enum'uyla birebir ayni degerler. */
export type Role = "VIEWER" | "EDITOR" | "ADMIN";

/**
 * Backend'in {@code LoginResponse} DTO'suyla ayni sekil. {@code token} sadece login'de ve
 * kullanici adi degisikliginde dolu gelir (bkz. AuthController#updateProfile javadoc'u) —
 * digerlerinde (me, avatar yukleme) null.
 */
export interface LoginResult {
  token: string | null;
  id: number;
  username: string;
  fullName: string | null;
  email: string | null;
  role: Role;
  hasAvatar: boolean;
}

export function login(username: string, password: string): Promise<LoginResult> {
  return apiPost<LoginResult>("/api/auth/login", { username, password });
}

/** Sayfa yenilendiginde localStorage'daki token'in hala gecerli olup olmadigini dogrulamak icin. */
export function me(): Promise<LoginResult> {
  return apiGet<LoginResult>("/api/auth/me");
}

/**
 * Kendi profilini gunceller (ad soyad, kullanici adi ve/veya e-posta). username degisirse donen
 * {@code token} dolu gelir — AuthProvider bunu sessizce eskisinin yerine yazmali.
 */
export function updateProfile(
  fullName: string | null,
  username: string | null,
  email: string | null
): Promise<LoginResult> {
  return apiPatch<LoginResult>("/api/auth/me", { fullName, username, email });
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return apiPatch<void>("/api/auth/me/password", { currentPassword, newPassword });
}

/**
 * Multipart yukleme — client.ts'teki apiPost/apiPatch her zaman JSON gövde varsayar, burada
 * ayri bir ham fetch cagrisi gerekiyor (bkz. auditLogs.ts'teki downloadBackupFile ile ayni
 * gerekce). Content-Type header'i BILEREK elle set edilmiyor — tarayici FormData'yi gorunce
 * boundary'li multipart/form-data'yi kendisi kurar, elle set edilirse boundary eksik kalirdi.
 */
export async function uploadAvatar(file: File): Promise<LoginResult> {
  const token = getAuthToken();
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch(`${API_BASE_URL}/api/auth/me/avatar`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
  if (!response.ok) {
    const body = await response.json();
    throw new ApiError(response.status, body.message, body.code, body.details);
  }
  return response.json();
}

/** {@code DELETE /api/auth/me/avatar} — foto zaten yoksa da 200 doner (idempotent), backend'de ayrica kontrol gerekmez. */
export function removeAvatar(): Promise<LoginResult> {
  return apiDelete<LoginResult>("/api/auth/me/avatar");
}

/** Backend'in {@code SessionResponse} DTO'suyla ayni sekil — bkz. requirement notu 9 ("Aktif Oturumlar"). */
export interface SessionInfo {
  jti: string;
  issuedAt: string;
  userAgent: string | null;
  current: boolean;
}

/** {@code GET /api/auth/sessions} — bu kullanicinin gecerli JWT'lerinin (login yapilan her cihaz) listesi. */
export function getSessions(): Promise<SessionInfo[]> {
  return apiGet<SessionInfo[]>("/api/auth/sessions");
}

/** {@code DELETE /api/auth/sessions/{jti}} — tek bir oturumu sonlandirir (su anki dahil olabilir). */
export function revokeSession(jti: string): Promise<void> {
  return apiDelete<void>(`/api/auth/sessions/${encodeURIComponent(jti)}`);
}

/** {@code POST /api/auth/sessions/revoke-others} — "Diger Cihazlardan Cikis Yap". */
export function revokeOtherSessions(): Promise<void> {
  return apiPost<void>("/api/auth/sessions/revoke-others", {});
}

/**
 * {@code <img src>} tarayicinin kendi istegine Authorization header'i ekleyemez (custom header
 * eklemenin tek yolu fetch/XHR) — bu yuzden avatari once kimlikli bir fetch ile blob olarak
 * cekip {@code URL.createObjectURL} ile gecici bir yerel URL'e ceviriyoruz, {@code <img>} ona
 * bakiyor. Avatar hic yuklenmemisse (404) null doner — caller placeholder gostermeli.
 * Caller, sayfadan ayrilirken donen URL'i {@code URL.revokeObjectURL} ile serbest birakmali.
 */
export async function fetchAvatarObjectUrl(): Promise<string | null> {
  const token = getAuthToken();
  const response = await fetch(`${API_BASE_URL}/api/auth/me/avatar`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    const body = await response.json();
    throw new ApiError(response.status, body.message, body.code, body.details);
  }
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}
