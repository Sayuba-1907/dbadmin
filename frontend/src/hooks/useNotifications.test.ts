import { act, renderHook, waitFor } from "@testing-library/react";
import { setAuthToken } from "../api/client";
import { useNotifications } from "./useNotifications";

/** Fake class used by useWebSocket in place of the real WebSocket, which the test can trigger manually. */
class MockWebSocket {
  static instances: MockWebSocket[] = [];
  url: string;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  close() {}
}

interface FakeNotification {
  id: number;
  tableId: number;
  tableName: string;
  triggeredByUsername: string;
  type: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

function mockFetch(count: number, list: FakeNotification[] = []) {
  global.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";
    const jsonResponse = (status: number, body: unknown) =>
      ({ ok: status < 400, status, json: async () => body }) as Response;

    if (url.endsWith("/api/notifications/unread-count")) {
      return jsonResponse(200, { count });
    }
    if (url.endsWith("/api/notifications?size=50")) {
      return jsonResponse(200, { content: list, totalElements: list.length });
    }
    if (/\/api\/notifications\/\d+\/read$/.test(url) && method === "PATCH") {
      return jsonResponse(204, undefined);
    }
    if (url.endsWith("/api/notifications/read") && method === "PATCH") {
      return jsonResponse(204, undefined);
    }
    throw new Error(`unexpected request: ${method} ${url}`);
  }) as jest.Mock;
}

function pushMessage(overrides: Partial<FakeNotification> = {}): Omit<FakeNotification, "isRead"> {
  const { isRead, ...rest } = {
    id: 1,
    tableId: 5,
    tableName: "students",
    triggeredByUsername: "editor1",
    type: "COLUMN_ADDED",
    message: '"age" column added to "students" table.',
    isRead: false,
    createdAt: "2026-08-07T10:00:00Z",
    ...overrides,
  };
  return rest;
}

beforeEach(() => {
  MockWebSocket.instances = [];
  (global as unknown as { WebSocket: unknown }).WebSocket = MockWebSocket;
  setAuthToken("test-token");
});

afterEach(() => {
  jest.restoreAllMocks();
  setAuthToken(null);
});

// requirement-websocket-notifications.md Req-2.5: only unread-count is fetched on mount, not the full list.
test("only unread-count is fetched on mount, full list is not fetched automatically", async () => {
  mockFetch(3, []);
  const { result } = renderHook(() => useNotifications(true));

  await waitFor(() => expect(result.current.count).toBe(3));

  expect(result.current.list).toEqual([]);
  expect(global.fetch).toHaveBeenCalledTimes(1);
});

// Req-2.4: WebSocket push updates the count AND the list locally — the server is not re-queried.
test("count and list are updated locally on WebSocket push, server is not queried", async () => {
  mockFetch(0, []);
  const onNewNotification = jest.fn();
  const { result } = renderHook(() => useNotifications(true, onNewNotification));
  await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

  act(() => {
    MockWebSocket.instances[0].onmessage?.({ data: JSON.stringify(pushMessage()) });
  });

  expect(result.current.count).toBe(1);
  expect(result.current.list).toHaveLength(1);
  expect(result.current.list[0]).toMatchObject({
    id: 1,
    tableName: "students",
    isRead: false,
  });
  expect(onNewNotification).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
  // No new request was made after the push — apart from the single count fetch on mount.
  expect(global.fetch).toHaveBeenCalledTimes(1);
});

test("markAsRead marks the matching item as read and decrements the count", async () => {
  mockFetch(0, []);
  const { result } = renderHook(() => useNotifications(true));
  await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1));

  act(() => {
    MockWebSocket.instances[0].onmessage?.({ data: JSON.stringify(pushMessage({ id: 7 })) });
  });
  expect(result.current.count).toBe(1);

  await act(async () => {
    await result.current.markAsRead(7);
  });

  expect(result.current.count).toBe(0);
  expect(result.current.list[0].isRead).toBe(true);
});

test("markAllAsRead marks every item in the list as read and resets the count", async () => {
  mockFetch(0, []);
  const { result } = renderHook(() => useNotifications(true));
  await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1));

  act(() => {
    MockWebSocket.instances[0].onmessage?.({ data: JSON.stringify(pushMessage({ id: 1 })) });
    MockWebSocket.instances[0].onmessage?.({ data: JSON.stringify(pushMessage({ id: 2 })) });
  });
  expect(result.current.count).toBe(2);

  await act(async () => {
    await result.current.markAllAsRead();
  });

  expect(result.current.count).toBe(0);
  expect(result.current.list.every((n) => n.isRead)).toBe(true);
});

// enabled=false: App.tsx must call this hook unconditionally (React Hooks rule) even before
// authentication resolves (loading/anonymous), but in that case it must neither query the server
// nor connect.
test("neither the count is fetched nor is a WebSocket connection made while enabled=false", async () => {
  mockFetch(5, []);
  renderHook(() => useNotifications(false));

  // Flush a microtask turn to assert that nothing happens.
  await act(async () => {
    await Promise.resolve();
  });

  expect(global.fetch).not.toHaveBeenCalled();
  expect(MockWebSocket.instances).toHaveLength(0);
});

test("a malformed/unexpected push frame is silently ignored", async () => {
  mockFetch(0, []);
  const { result } = renderHook(() => useNotifications(true));
  await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1));

  act(() => {
    MockWebSocket.instances[0].onmessage?.({ data: "invalid-json{{{" });
  });

  expect(result.current.count).toBe(0);
  expect(result.current.list).toEqual([]);
});
