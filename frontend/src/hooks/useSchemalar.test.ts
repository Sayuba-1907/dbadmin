import { act, renderHook, waitFor } from "@testing-library/react";
import { useSchemalar } from "./useSchemalar";

interface FakeSchema {
  id: number;
  name: string;
  tabloSayisi: number;
}

/**
 * Dashboard.test.tsx'teki gibi gercek fetch'i, URL/method'a gore yanit ureten kucuk bir sahte
 * backend'le degistiriyoruz — projede jest.mock kullanilmiyor, davranis HTTP sinirinda test
 * ediliyor.
 */
function mockFetch(seed: FakeSchema[]) {
  let schemalar = [...seed];
  let nextId = Math.max(0, ...schemalar.map((s) => s.id)) + 1;

  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";
    const jsonResponse = (status: number, body: unknown) =>
      ({
        ok: status < 400,
        status,
        json: async () => body,
      }) as Response;

    if (url.endsWith("/api/schemalar") && method === "GET") {
      return jsonResponse(200, schemalar);
    }
    if (url.endsWith("/api/schemalar") && method === "POST") {
      const { name } = JSON.parse(init!.body as string);
      const created = { id: nextId++, name, tabloSayisi: 0 };
      schemalar = [...schemalar, created];
      return jsonResponse(201, created);
    }
    const patchMatch = url.match(/\/api\/schemalar\/(\d+)$/);
    if (patchMatch && method === "PATCH") {
      const id = Number(patchMatch[1]);
      const { name } = JSON.parse(init!.body as string);
      schemalar = schemalar.map((s) => (s.id === id ? { ...s, name } : s));
      return jsonResponse(
        200,
        schemalar.find((s) => s.id === id)
      );
    }
    if (patchMatch && method === "DELETE") {
      const id = Number(patchMatch[1]);
      schemalar = schemalar.filter((s) => s.id !== id);
      return jsonResponse(204, undefined);
    }
    throw new Error(`beklenmeyen istek: ${method} ${url}`);
  }) as jest.Mock;
}

afterEach(() => {
  jest.restoreAllMocks();
});

test("ilk render'da schemalar cekilir", async () => {
  mockFetch([{ id: 1, name: "okul", tabloSayisi: 2 }]);

  const { result } = renderHook(() => useSchemalar());

  expect(result.current.yukleniyor).toBe(true);
  await waitFor(() => expect(result.current.yukleniyor).toBe(false));
  expect(result.current.schemalar).toEqual([{ id: 1, name: "okul", tabloSayisi: 2 }]);
});

test("createSchema cagrisindan sonra liste yenilenir", async () => {
  mockFetch([{ id: 1, name: "okul", tabloSayisi: 0 }]);
  const { result } = renderHook(() => useSchemalar());
  await waitFor(() => expect(result.current.yukleniyor).toBe(false));

  await act(async () => {
    await result.current.createSchema("kutuphane");
  });

  expect(result.current.schemalar.map((s) => s.name)).toEqual(["okul", "kutuphane"]);
});

test("deleteSchema cagrisindan sonra liste yenilenir", async () => {
  mockFetch([
    { id: 1, name: "okul", tabloSayisi: 0 },
    { id: 2, name: "kutuphane", tabloSayisi: 0 },
  ]);
  const { result } = renderHook(() => useSchemalar());
  await waitFor(() => expect(result.current.yukleniyor).toBe(false));

  await act(async () => {
    await result.current.deleteSchema(2);
  });

  expect(result.current.schemalar.map((s) => s.name)).toEqual(["okul"]);
});

/** Req-2.4: hook hatayi yutmaz, firlatir — "kullaniciya ne gosterilecek" karari Dashboard'da kalir. */
test("createSchema backend hata donerse hata disari firlatilir", async () => {
  mockFetch([{ id: 1, name: "okul", tabloSayisi: 0 }]);
  const { result } = renderHook(() => useSchemalar());
  await waitFor(() => expect(result.current.yukleniyor).toBe(false));

  const orijinalFetch = global.fetch as jest.Mock;
  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    if (method === "POST") {
      return {
        ok: false,
        status: 409,
        json: async () => ({
          timestamp: "2026-01-01T00:00:00Z",
          status: 409,
          error: "Conflict",
          message: "a schema named 'okul' already exists",
          code: "CONFLICT_DUPLICATE_SCHEMA_NAME",
        }),
      } as Response;
    }
    return orijinalFetch(input, init);
  }) as jest.Mock;

  await expect(
    act(async () => {
      await result.current.createSchema("okul");
    })
  ).rejects.toThrow();
});
