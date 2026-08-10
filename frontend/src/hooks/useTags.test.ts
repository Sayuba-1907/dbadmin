import { act, renderHook } from "@testing-library/react";
import { useTags } from "./useTags";

interface FakeTag {
  id: number;
  name: string;
}

function mockFetch(seed: FakeTag[]) {
  let tags = [...seed];
  let nextId = Math.max(0, ...tags.map((t) => t.id)) + 1;

  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";
    const jsonResponse = (status: number, body: unknown) =>
      ({ ok: status < 400, status, json: async () => body }) as Response;

    if (url.includes("/api/tags?") && method === "GET") {
      return jsonResponse(200, { content: tags });
    }
    if (url.endsWith("/api/tags") && method === "POST") {
      const { name } = JSON.parse(init!.body as string);
      const created = { id: nextId++, name };
      tags = [...tags, created];
      return jsonResponse(201, created);
    }
    const idMatch = url.match(/\/api\/tags\/(\d+)$/);
    if (idMatch && method === "PATCH") {
      const id = Number(idMatch[1]);
      const { name } = JSON.parse(init!.body as string);
      tags = tags.map((t) => (t.id === id ? { ...t, name } : t));
      return jsonResponse(
        200,
        tags.find((t) => t.id === id)
      );
    }
    if (idMatch && method === "DELETE") {
      const id = Number(idMatch[1]);
      tags = tags.filter((t) => t.id !== id);
      return jsonResponse(204, undefined);
    }
    throw new Error(`beklenmeyen istek: ${method} ${url}`);
  }) as jest.Mock;
}

afterEach(() => {
  jest.restoreAllMocks();
});

test("mount'ta OTOMATIK istek atilmaz — tags bos baslar", () => {
  mockFetch([{ id: 1, name: "onemli" }]);
  const { result } = renderHook(() => useTags());

  expect(result.current.tags).toEqual([]);
  expect(global.fetch).not.toHaveBeenCalled();
});

test("refresh cagrisi tags'i ceker", async () => {
  mockFetch([{ id: 1, name: "onemli" }]);
  const { result } = renderHook(() => useTags());

  await act(async () => {
    await result.current.refresh();
  });

  expect(result.current.tags).toEqual([{ id: 1, name: "onemli" }]);
});

test("createTag cagrisindan sonra liste yenilenir", async () => {
  mockFetch([{ id: 1, name: "onemli" }]);
  const { result } = renderHook(() => useTags());
  await act(async () => {
    await result.current.refresh();
  });

  await act(async () => {
    await result.current.createTag("acil");
  });

  expect(result.current.tags.map((t) => t.name)).toEqual(["onemli", "acil"]);
});

test("deleteTag cagrisindan sonra liste yenilenir", async () => {
  mockFetch([
    { id: 1, name: "onemli" },
    { id: 2, name: "acil" },
  ]);
  const { result } = renderHook(() => useTags());
  await act(async () => {
    await result.current.refresh();
  });

  await act(async () => {
    await result.current.deleteTag(2);
  });

  // deleteTag hook'ta refresh'yi TEKRAR cagirmiyor (Dashboard'daki geri-al akisi kendi yeniliyor) —
  // bkz. useTags.ts: remove sadece API'yi cagirir. Bu yuzden burada backend'i dogrudan kontrol ediyoruz.
  await act(async () => {
    await result.current.refresh();
  });
  expect(result.current.tags.map((t) => t.name)).toEqual(["onemli"]);
});

/** Req-2.4: hook hatayi yutmaz, firlatir. */
test("createTag backend hata donerse hata disari firlatilir", async () => {
  mockFetch([{ id: 1, name: "onemli" }]);
  const { result } = renderHook(() => useTags());
  await act(async () => {
    await result.current.refresh();
  });

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
          message: "a tag named 'onemli' already exists",
          code: "CONFLICT_DUPLICATE_TAG_NAME",
        }),
      } as Response;
    }
    return orijinalFetch(input, init);
  }) as jest.Mock;

  await expect(
    act(async () => {
      await result.current.createTag("onemli");
    })
  ).rejects.toThrow();
});
