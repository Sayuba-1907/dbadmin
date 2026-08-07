import { act, renderHook } from "@testing-library/react";
import { useTables } from "./useTables";

interface FakeKolon {
  id: number;
  name: string;
  type: string;
  tagId: number | null;
  tagName: string | null;
  primaryKey: boolean;
}

interface FakeTablo {
  id: number;
  name: string;
  schemaId: number;
  schemaName: string;
  columns: FakeKolon[];
  updatedAt: string | null;
}

/** Dashboard.test.tsx/useSchemas.test.ts ile ayni yaklasim: gercek fetch, jest.mock yok. */
function mockFetch(seed: FakeTablo) {
  let tablo = { ...seed };

  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";
    const jsonResponse = (status: number, body: unknown) =>
      ({ ok: status < 400, status, json: async () => body }) as Response;

    if (url.endsWith(`/api/tables/${tablo.id}`) && method === "GET") {
      return jsonResponse(200, tablo);
    }
    if (url.endsWith("/api/tables") && method === "POST") {
      const body = JSON.parse(init!.body as string);
      tablo = { ...tablo, name: body.name, columns: [] };
      return jsonResponse(201, tablo);
    }
    if (url.endsWith(`/api/tables/${tablo.id}/changes`) && method === "PATCH") {
      const body = JSON.parse(init!.body as string);
      if (body.newName) {
        tablo = { ...tablo, name: body.newName };
      }
      return jsonResponse(200, tablo);
    }
    if (url.endsWith(`/api/tables/${tablo.id}/schema`) && method === "PATCH") {
      const body = JSON.parse(init!.body as string);
      tablo = { ...tablo, schemaId: body.schemaId };
      return jsonResponse(200, tablo);
    }
    if (url.endsWith(`/api/tables/${tablo.id}`) && method === "DELETE") {
      return jsonResponse(204, undefined);
    }
    throw new Error(`beklenmeyen istek: ${method} ${url}`);
  }) as jest.Mock;
}

afterEach(() => {
  jest.restoreAllMocks();
});

test("select cagrisi tablo detayini ceker", async () => {
  mockFetch({
    id: 1,
    name: "ogrenciler",
    schemaId: 10,
    schemaName: "okul",
    columns: [],
    updatedAt: null,
  });
  const { result } = renderHook(() => useTables());

  await act(async () => {
    await result.current.select(1);
  });

  expect(result.current.selectedTable?.name).toBe("ogrenciler");
});

test("clearSelection sonrasi selectedTable null olur", async () => {
  mockFetch({
    id: 1,
    name: "ogrenciler",
    schemaId: 10,
    schemaName: "okul",
    columns: [],
    updatedAt: null,
  });
  const { result } = renderHook(() => useTables());
  await act(async () => {
    await result.current.select(1);
  });

  act(() => {
    result.current.clearSelection();
  });

  expect(result.current.selectedTable).toBeNull();
});

test("create sonrasi selectedTable yeni tabloya doner", async () => {
  mockFetch({
    id: 1,
    name: "eski",
    schemaId: 10,
    schemaName: "okul",
    columns: [],
    updatedAt: null,
  });
  const { result } = renderHook(() => useTables());

  await act(async () => {
    await result.current.create("yeni_tablo", [{ name: "ad", type: "text" }], 10);
  });

  expect(result.current.selectedTable?.name).toBe("yeni_tablo");
});

test("applyChanges sonrasi selectedTable guncellenir", async () => {
  mockFetch({
    id: 1,
    name: "eski_isim",
    schemaId: 10,
    schemaName: "okul",
    columns: [],
    updatedAt: null,
  });
  const { result } = renderHook(() => useTables());
  await act(async () => {
    await result.current.select(1);
  });

  await act(async () => {
    await result.current.applyChanges(1, {
      newName: "yeni_isim",
      newSchemaId: null,
      columnIdsToDelete: [],
      columnsToAdd: [],
      columnsToUpdate: [],
    });
  });

  expect(result.current.selectedTable?.name).toBe("yeni_isim");
});

/** Req-2.4: hook hatayi yutmaz, firlatir. */
test("select backend hata donerse hata disari firlatilir", async () => {
  global.fetch = jest.fn(async () => ({
    ok: false,
    status: 404,
    json: async () => ({
      timestamp: "2026-01-01T00:00:00Z",
      status: 404,
      error: "Not Found",
      message: "tablo not found: 999",
      code: "NOT_FOUND_TABLE",
    }),
  })) as jest.Mock;
  const { result } = renderHook(() => useTables());

  await expect(
    act(async () => {
      await result.current.select(999);
    })
  ).rejects.toThrow();
});
