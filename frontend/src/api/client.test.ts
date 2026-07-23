import { apiDelete, apiGet, apiPost, ApiError } from "./client";

afterEach(() => {
  jest.restoreAllMocks();
});

function mockFetchOnce(response: Partial<Response>) {
  global.fetch = jest.fn().mockResolvedValue(response) as unknown as typeof fetch;
}

test("apiGet basarili yanitta json doner", async () => {
  mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1 }) });

  const result = await apiGet<{ id: number }>("/api/tablolar/1");

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

  await expect(apiPost("/api/tablolar", { name: "x" })).rejects.toMatchObject({
    status: 409,
    message: "tablo adi zaten kullaniliyor",
  });
  await expect(apiPost("/api/tablolar", { name: "x" })).rejects.toBeInstanceOf(ApiError);
});

test("apiDelete 204 yanitinda body okumaya calismaz", async () => {
  mockFetchOnce({ ok: true, status: 204 });

  await expect(apiDelete("/api/tablolar/1")).resolves.toBeUndefined();
});
