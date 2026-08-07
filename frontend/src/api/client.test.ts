import { apiDelete, apiGet, apiPost, ApiError, setAuthToken, setOnUnauthorized } from "./client";

afterEach(() => {
  jest.restoreAllMocks();
  // authToken modul-seviyesinde bir degisken (testler arasinda sifirlanmaz) — bir testte
  // set edilen token bir sonrakine sizmasin diye her testten sonra temizliyoruz.
  setAuthToken(null);
  setOnUnauthorized(null);
});

function mockFetchOnce(response: Partial<Response>) {
  global.fetch = jest.fn().mockResolvedValue(response) as unknown as typeof fetch;
}

test("apiGet basarili yanitta json doner", async () => {
  mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1 }) });

  const result = await apiGet<{ id: number }>("/api/tables/1");

  expect(result).toEqual({ id: 1 });
});

test("apiPost basarisiz yanitta ApiError firlatir (status + message)", async () => {
  mockFetchOnce({
    ok: false,
    status: 409,
    json: async () => ({
      timestamp: "2026-01-01T00:00:00Z",
      status: 409,
      error: "Conflict",
      message: "tablo adi zaten kullaniliyor",
    }),
  });

  await expect(apiPost("/api/tables", { name: "x" })).rejects.toMatchObject({
    status: 409,
    message: "tablo adi zaten kullaniliyor",
  });
  await expect(apiPost("/api/tables", { name: "x" })).rejects.toBeInstanceOf(ApiError);
});

test("apiDelete 204 yanitinda body okumaya calismaz", async () => {
  mockFetchOnce({ ok: true, status: 204 });

  await expect(apiDelete("/api/tables/1")).resolves.toBeUndefined();
});

test("setAuthToken ile bir token verilmisse istek Authorization basligi tasir", async () => {
  const fetchMock = jest.fn().mockResolvedValue({ ok: true, status: 200, json: async () => [] });
  global.fetch = fetchMock as unknown as typeof fetch;
  setAuthToken("ornek-jwt");

  await apiGet("/api/tables");

  expect(fetchMock).toHaveBeenCalledWith(
    expect.any(String),
    expect.objectContaining({
      headers: expect.objectContaining({ Authorization: "Bearer ornek-jwt" }),
    })
  );
});

test("token yoksa Authorization basligi hic eklenmez", async () => {
  const fetchMock = jest.fn().mockResolvedValue({ ok: true, status: 200, json: async () => [] });
  global.fetch = fetchMock as unknown as typeof fetch;

  await apiGet("/api/tables");

  const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
  expect(headers.Authorization).toBeUndefined();
});

test("AUTH_REQUIRED kodlu 401'de onUnauthorized kancasi tetiklenir", async () => {
  mockFetchOnce({
    ok: false,
    status: 401,
    json: async () => ({
      timestamp: "2026-01-01T00:00:00Z",
      status: 401,
      error: "Unauthorized",
      message: "authentication is required, send a valid Bearer token",
      code: "AUTH_REQUIRED",
    }),
  });
  const onUnauthorized = jest.fn();
  setOnUnauthorized(onUnauthorized);

  await expect(apiGet("/api/tables")).rejects.toBeInstanceOf(ApiError);

  expect(onUnauthorized).toHaveBeenCalledTimes(1);
});

test("AUTH_INVALID_CREDENTIALS kodlu 401'de (yanlis parola) onUnauthorized TETIKLENMEZ", async () => {
  // Bu ayrim onemli: login formunda yanlis parola girmek "oturumdan atilmak" degildir —
  // henuz bir oturum yok ki sonlansin. onUnauthorized burada da tetiklenseydi, zaten
  // "anonymous" durumundaki AuthProvider'i gereksiz yere logout() cagirmaya iterdi.
  mockFetchOnce({
    ok: false,
    status: 401,
    json: async () => ({
      timestamp: "2026-01-01T00:00:00Z",
      status: 401,
      error: "Unauthorized",
      message: "invalid username or password",
      code: "AUTH_INVALID_CREDENTIALS",
    }),
  });
  const onUnauthorized = jest.fn();
  setOnUnauthorized(onUnauthorized);

  await expect(apiPost("/api/auth/login", { username: "x", parola: "y" })).rejects.toBeInstanceOf(
    ApiError
  );

  expect(onUnauthorized).not.toHaveBeenCalled();
});
