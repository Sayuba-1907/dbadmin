import { act, renderHook } from "@testing-library/react";
import { useUsers } from "./useUsers";

interface FakeKullanici {
  id: number;
  username: string;
  role: "VIEWER" | "EDITOR" | "ADMIN";
}

function mockFetch(seed: FakeKullanici[]) {
  let users = [...seed];
  let nextId = Math.max(0, ...users.map((k) => k.id)) + 1;

  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";
    const jsonResponse = (status: number, body: unknown) =>
      ({ ok: status < 400, status, json: async () => body }) as Response;

    if (url.includes("/api/users?") && method === "GET") {
      return jsonResponse(200, { content: users });
    }
    if (url.endsWith("/api/users") && method === "POST") {
      const body = JSON.parse(init!.body as string);
      const created = { id: nextId++, username: body.username, role: body.role };
      users = [...users, created];
      return jsonResponse(201, created);
    }
    const rolMatch = url.match(/\/api\/users\/(\d+)\/role$/);
    if (rolMatch && method === "PATCH") {
      const id = Number(rolMatch[1]);
      const body = JSON.parse(init!.body as string);
      users = users.map((k) => (k.id === id ? { ...k, role: body.role } : k));
      return jsonResponse(
        200,
        users.find((k) => k.id === id)
      );
    }
    const idMatch = url.match(/\/api\/users\/(\d+)$/);
    if (idMatch && method === "DELETE") {
      const id = Number(idMatch[1]);
      users = users.filter((k) => k.id !== id);
      return jsonResponse(204, undefined);
    }
    throw new Error(`beklenmeyen istek: ${method} ${url}`);
  }) as jest.Mock;
}

afterEach(() => {
  jest.restoreAllMocks();
});

test("mount'ta OTOMATIK istek atilmaz — users bos baslar", () => {
  mockFetch([{ id: 1, username: "admin", role: "ADMIN" }]);
  const { result } = renderHook(() => useUsers());

  expect(result.current.users).toEqual([]);
  expect(global.fetch).not.toHaveBeenCalled();
});

test("refresh cagrisi kullanicilari ceker", async () => {
  mockFetch([{ id: 1, username: "admin", role: "ADMIN" }]);
  const { result } = renderHook(() => useUsers());

  await act(async () => {
    await result.current.refresh();
  });

  expect(result.current.users).toEqual([{ id: 1, username: "admin", role: "ADMIN" }]);
});

test("changeUserRole cagrisindan sonra liste yenilenir", async () => {
  mockFetch([{ id: 2, username: "mehmet", role: "VIEWER" }]);
  const { result } = renderHook(() => useUsers());
  await act(async () => {
    await result.current.refresh();
  });

  await act(async () => {
    await result.current.changeUserRole(2, "EDITOR");
  });

  expect(result.current.users[0].role).toBe("EDITOR");
});

/** Req-2.4: hook hatayi yutmaz, firlatir — ör. CONFLICT_LAST_ADMIN senaryosu. */
test("changeUserRole backend hata donerse hata disari firlatilir", async () => {
  mockFetch([{ id: 1, username: "admin", role: "ADMIN" }]);
  const { result } = renderHook(() => useUsers());
  await act(async () => {
    await result.current.refresh();
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
      await result.current.changeUserRole(1, "VIEWER");
    })
  ).rejects.toThrow();
});
