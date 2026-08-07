export const API_BASE_URL = "http://localhost:8081";

/**
 * Su anki JWT — AuthProvider'in disardan set ettigi bir modul-seviyesi degisken. client.ts
 * bilerek AuthProvider'i import etmez (auth/AuthProvider zaten login/me icin bu dosyadaki
 * apiPost/apiGet'i cagirir; tersten bir import döngüye yol acardi). Bunun yerine AuthProvider
 * {@link setAuthToken}'i cagirarak token'i buraya bildirir — akis tek yonlu kalir.
 */
let authToken: string | null = null;

/** AuthProvider login/logout olduğunda ya da sayfa acilista localStorage'dan okudugunda cagirir. */
export function setAuthToken(token: string | null) {
  authToken = token;
}

/** useWebSocket icin: /ws handshake'i header degil query param tasidigi icin (bkz. o hook'un javadoc'u) token'in kendisine ihtiyac var. */
export function getAuthToken(): string | null {
  return authToken;
}

/**
 * 401 aldigimizda (token suresi dolmus/gecersiz) AuthProvider'in kendi state'ini de temizlemesi
 * icin bir kanca. Burada dogrudan AuthProvider'i cagiramayiz (yine dongu), o yuzden AuthProvider
 * kendini bu degiskene kaydeder.
 */
let onUnauthorized: (() => void) | null = null;

export function setOnUnauthorized(handler: (() => void) | null) {
  onUnauthorized = handler;
}

/** Backend'in {@code ErrorResponse} DTO'suyla birebir ayni sekil — hata govdesinin JSON karsiligi. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  code?: string;
  details?: Record<string, string>;
}

/**
 * Backend'ten 4xx/5xx donduğunde firlatilan ozel hata tipi. `status` alani sayesinde
 * caller (mesela Dashboard) 409'u 400'den ayirt edip farkli renkte bildirim gosterebilir.
 * `code`/`details` — backend'in i18n icin ekledigi makine-okunur alanlar: `code` hangi ceviri
 * anahtarinin kullanilacagini (bkz. i18n/locales/*.json'daki "errors" bolumu), `details` ise
 * o cevirinin icine (mesela tablo adi) doldurulacak degiskenleri tasir. `message` her zaman
 * backend'in Ingilizce metni — `code` taninmiyorsa (beklenmedik/eski bir hata) buna dusulur.
 */
export class ApiError extends Error {
  status: number;
  code?: string;
  details?: Record<string, string>;

  constructor(status: number, message: string, code?: string, details?: Record<string, string>) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

/**
 * Tum fetch cagrilarinin gectigi tek ortak nokta — her endpoint fonksiyonu (apiGet/apiPost/...)
 * bunu kullanir, boylece base URL, header, hata firlatma ve 204/JSON ayrimi tek yerde yasar.
 * response.ok false ise (backend 4xx/5xx donduyse) JSON govdeyi okuyup {@link ApiError} firlatir;
 * 204 No Content ise (govde yok) undefined doner, aksi halde govdeyi JSON olarak parse eder.
 */
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers,
    ...options,
  });

  if (!response.ok) {
    const body: ApiErrorBody = await response.json();
    // AUTH_REQUIRED: elimizdeki token gecersiz/suresi dolmus (login denemesinin kendi 401'i
    // AUTH_INVALID_CREDENTIALS doner, o farkli bir durum — kullanicinin parolayi yanlis
    // yazmasi "oturumdan atilma" degildir). Sadece bu durumda AuthProvider'i uyarip
    // kullaniciyi giris ekranina geri dondururuz.
    if (response.status === 401 && body.code === "AUTH_REQUIRED") {
      onUnauthorized?.();
    }
    throw new ApiError(response.status, body.message, body.code, body.details);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export function apiGet<T>(path: string): Promise<T> {
  return request<T>(path);
}

export function apiPost<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: "POST", body: JSON.stringify(body) });
}

export function apiPatch<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: "PATCH", body: JSON.stringify(body) });
}

export function apiDelete(path: string): Promise<void> {
  return request<void>(path, { method: "DELETE" });
}
