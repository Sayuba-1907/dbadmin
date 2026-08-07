import { act, renderHook, waitFor } from "@testing-library/react";
import { useSchemas } from "./useSchemas";

interface FakeSchema {
  id: number;
  name: string;
  tableCount: number;
}

/**
 * Dashboard.test.tsx'teki gibi gercek fetch'i, URL/method'a gore yanit ureten kucuk bir sahte
 * backend'le degistiriyoruz — projede jest.mock kullanilmiyor, davranis HTTP sinirinda test
 * ediliyor.
 */
function mockFetch(seed: FakeSchema[]) {
  let schemas = [...seed];
  let nextId = Math.max(0, ...schemas.map((s) => s.id)) + 1;

  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";
    const jsonResponse = (status: number, body: unknown) =>
      ({
        ok: status < 400,
        status,
        json: async () => body,
      }) as Response;

    if (url.endsWith("/api/schemas") && method === "GET") {
      return jsonResponse(200, schemas);
    }
    if (url.endsWith("/api/schemas") && method === "POST") {
      const { name } = JSON.parse(init!.body as string);
      const created = { id: nextId++, name, tableCount: 0 };
      schemas = [...schemas, created];
      return jsonResponse(201, created);
    }
    const patchMatch = url.match(/\/api\/schemas\/(\d+)$/);
    if (patchMatch && method === "PATCH") {
      const id = Number(patchMatch[1]);
      const { name } = JSON.parse(init!.body as string);
      schemas = schemas.map((s) => (s.id === id ? { ...s, name } : s));
      return jsonResponse(
        200,
        schemas.find((s) => s.id === id)
      );
    }
    if (patchMatch && method === "DELETE") {
      const id = Number(patchMatch[1]);
      schemas = schemas.filter((s) => s.id !== id);
      return jsonResponse(204, undefined);
    }
    throw new Error(`beklenmeyen istek: ${method} ${url}`);
  }) as jest.Mock;
}

afterEach(() => {
  jest.restoreAllMocks();
});

test("ilk render'da schemas cekilir", async () => {
  mockFetch([{ id: 1, name: "okul", tableCount: 2 }]);

  const { result } = renderHook(() => useSchemas());

  expect(result.current.loading).toBe(true);
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.schemas).toEqual([{ id: 1, name: "okul", tableCount: 2 }]);
});

test("createSchema cagrisindan sonra liste yenilenir", async () => {
  mockFetch([{ id: 1, name: "okul", tableCount: 0 }]);
  const { result } = renderHook(() => useSchemas());
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(async () => {
    await result.current.createSchema("kutuphane");
  });

  expect(result.current.schemas.map((s) => s.name)).toEqual(["okul", "kutuphane"]);
});

test("deleteSchema cagrisindan sonra liste yenilenir", async () => {
  mockFetch([
    { id: 1, name: "okul", tableCount: 0 },
    { id: 2, name: "kutuphane", tableCount: 0 },
  ]);
  const { result } = renderHook(() => useSchemas());
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(async () => {
    await result.current.deleteSchema(2);
  });

  expect(result.current.schemas.map((s) => s.name)).toEqual(["okul"]);
});

/** Req-2.4: hook hatayi yutmaz, firlatir — "kullaniciya ne gosterilecek" karari Dashboard'da kalir. */
test("createSchema backend hata donerse hata disari firlatilir", async () => {
  mockFetch([{ id: 1, name: "okul", tableCount: 0 }]);
  const { result } = renderHook(() => useSchemas());
  await waitFor(() => expect(result.current.loading).toBe(false));

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
