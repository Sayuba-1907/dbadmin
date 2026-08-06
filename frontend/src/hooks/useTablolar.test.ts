import { act, renderHook } from "@testing-library/react";
import { useTablolar } from "./useTablolar";

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
  kolonlar: FakeKolon[];
  updatedAt: string | null;
}

/** Dashboard.test.tsx/useSchemalar.test.ts ile ayni yaklasim: gercek fetch, jest.mock yok. */
function mockFetch(seed: FakeTablo) {
  let tablo = { ...seed };

  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";
    const jsonResponse = (status: number, body: unknown) =>
      ({ ok: status < 400, status, json: async () => body }) as Response;

    if (url.endsWith(`/api/tablolar/${tablo.id}`) && method === "GET") {
      return jsonResponse(200, tablo);
    }
    if (url.endsWith("/api/tablolar") && method === "POST") {
      const body = JSON.parse(init!.body as string);
      tablo = { ...tablo, name: body.name, kolonlar: [] };
      return jsonResponse(201, tablo);
    }
    if (url.endsWith(`/api/tablolar/${tablo.id}/degisiklikler`) && method === "PATCH") {
      const body = JSON.parse(init!.body as string);
      if (body.yeniIsim) {
        tablo = { ...tablo, name: body.yeniIsim };
      }
      return jsonResponse(200, tablo);
    }
    if (url.endsWith(`/api/tablolar/${tablo.id}/schema`) && method === "PATCH") {
      const body = JSON.parse(init!.body as string);
      tablo = { ...tablo, schemaId: body.schemaId };
      return jsonResponse(200, tablo);
    }
    if (url.endsWith(`/api/tablolar/${tablo.id}`) && method === "DELETE") {
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
    kolonlar: [],
    updatedAt: null,
  });
  const { result } = renderHook(() => useTablolar());

  await act(async () => {
    await result.current.select(1);
  });

  expect(result.current.selectedTablo?.name).toBe("ogrenciler");
});

test("clearSelection sonrasi selectedTablo null olur", async () => {
  mockFetch({
    id: 1,
    name: "ogrenciler",
    schemaId: 10,
    schemaName: "okul",
    kolonlar: [],
    updatedAt: null,
  });
  const { result } = renderHook(() => useTablolar());
  await act(async () => {
    await result.current.select(1);
  });

  act(() => {
    result.current.clearSelection();
  });

  expect(result.current.selectedTablo).toBeNull();
});

test("create sonrasi selectedTablo yeni tabloya doner", async () => {
  mockFetch({
    id: 1,
    name: "eski",
    schemaId: 10,
    schemaName: "okul",
    kolonlar: [],
    updatedAt: null,
  });
  const { result } = renderHook(() => useTablolar());

  await act(async () => {
    await result.current.create("yeni_tablo", [{ name: "ad", type: "text" }], 10);
  });

  expect(result.current.selectedTablo?.name).toBe("yeni_tablo");
});

test("applyChanges sonrasi selectedTablo guncellenir", async () => {
  mockFetch({
    id: 1,
    name: "eski_isim",
    schemaId: 10,
    schemaName: "okul",
    kolonlar: [],
    updatedAt: null,
  });
  const { result } = renderHook(() => useTablolar());
  await act(async () => {
    await result.current.select(1);
  });

  await act(async () => {
    await result.current.applyChanges(1, {
      yeniIsim: "yeni_isim",
      yeniSchemaId: null,
      silinecekKolonIdler: [],
      eklenecekKolonlar: [],
      guncellenecekKolonlar: [],
    });
  });

  expect(result.current.selectedTablo?.name).toBe("yeni_isim");
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
  const { result } = renderHook(() => useTablolar());

  await expect(
    act(async () => {
      await result.current.select(999);
    })
  ).rejects.toThrow();
});
