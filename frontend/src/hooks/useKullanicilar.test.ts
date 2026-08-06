import { act, renderHook } from "@testing-library/react";
import { useKullanicilar } from "./useKullanicilar";

interface FakeKullanici {
  id: number;
  kullaniciAdi: string;
  rol: "VIEWER" | "EDITOR" | "ADMIN";
}

function mockFetch(seed: FakeKullanici[]) {
  let kullanicilar = [...seed];
  let nextId = Math.max(0, ...kullanicilar.map((k) => k.id)) + 1;

  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";
    const jsonResponse = (status: number, body: unknown) =>
      ({ ok: status < 400, status, json: async () => body }) as Response;

    if (url.endsWith("/api/kullanicilar") && method === "GET") {
      return jsonResponse(200, kullanicilar);
    }
    if (url.endsWith("/api/kullanicilar") && method === "POST") {
      const body = JSON.parse(init!.body as string);
      const created = { id: nextId++, kullaniciAdi: body.kullaniciAdi, rol: body.rol };
      kullanicilar = [...kullanicilar, created];
      return jsonResponse(201, created);
    }
    const rolMatch = url.match(/\/api\/kullanicilar\/(\d+)\/rol$/);
    if (rolMatch && method === "PATCH") {
      const id = Number(rolMatch[1]);
      const body = JSON.parse(init!.body as string);
      kullanicilar = kullanicilar.map((k) => (k.id === id ? { ...k, rol: body.rol } : k));
      return jsonResponse(
        200,
        kullanicilar.find((k) => k.id === id)
      );
    }
    const idMatch = url.match(/\/api\/kullanicilar\/(\d+)$/);
    if (idMatch && method === "DELETE") {
      const id = Number(idMatch[1]);
      kullanicilar = kullanicilar.filter((k) => k.id !== id);
      return jsonResponse(204, undefined);
    }
    throw new Error(`beklenmeyen istek: ${method} ${url}`);
  }) as jest.Mock;
}

afterEach(() => {
  jest.restoreAllMocks();
});

test("mount'ta OTOMATIK istek atilmaz — kullanicilar bos baslar", () => {
  mockFetch([{ id: 1, kullaniciAdi: "admin", rol: "ADMIN" }]);
  const { result } = renderHook(() => useKullanicilar());

  expect(result.current.kullanicilar).toEqual([]);
  expect(global.fetch).not.toHaveBeenCalled();
});

test("yenile cagrisi kullanicilari ceker", async () => {
  mockFetch([{ id: 1, kullaniciAdi: "admin", rol: "ADMIN" }]);
  const { result } = renderHook(() => useKullanicilar());

  await act(async () => {
    await result.current.yenile();
  });

  expect(result.current.kullanicilar).toEqual([{ id: 1, kullaniciAdi: "admin", rol: "ADMIN" }]);
});

test("changeKullaniciRol cagrisindan sonra liste yenilenir", async () => {
  mockFetch([{ id: 2, kullaniciAdi: "mehmet", rol: "VIEWER" }]);
  const { result } = renderHook(() => useKullanicilar());
  await act(async () => {
    await result.current.yenile();
  });

  await act(async () => {
    await result.current.changeKullaniciRol(2, "EDITOR");
  });

  expect(result.current.kullanicilar[0].rol).toBe("EDITOR");
});

/** Req-2.4: hook hatayi yutmaz, firlatir — ör. CONFLICT_LAST_ADMIN senaryosu. */
test("changeKullaniciRol backend hata donerse hata disari firlatilir", async () => {
  mockFetch([{ id: 1, kullaniciAdi: "admin", rol: "ADMIN" }]);
  const { result } = renderHook(() => useKullanicilar());
  await act(async () => {
    await result.current.yenile();
  });

  const orijinalFetch = global.fetch as jest.Mock;
  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    if (method === "PATCH") {
      return {
        ok: false,
        status: 409,
        json: async () => ({
          timestamp: "2026-01-01T00:00:00Z",
          status: 409,
          error: "Conflict",
          message: "the last remaining admin cannot be removed or demoted",
          code: "CONFLICT_LAST_ADMIN",
        }),
      } as Response;
    }
    return orijinalFetch(input, init);
  }) as jest.Mock;

  await expect(
    act(async () => {
      await result.current.changeKullaniciRol(1, "VIEWER");
    })
  ).rejects.toThrow();
});
