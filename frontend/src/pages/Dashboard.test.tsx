import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Dashboard } from "./Dashboard";
import { NotificationProvider } from "../notifications/NotificationProvider";

afterEach(() => {
  jest.restoreAllMocks();
});

function mockFetchSequence(responses: Partial<Response>[]) {
  const fn = jest.fn();
  responses.forEach((response) => fn.mockResolvedValueOnce(response));
  global.fetch = fn as unknown as typeof fetch;
  return fn;
}

function renderDashboard() {
  return render(
    <NotificationProvider>
      <Dashboard />
    </NotificationProvider>
  );
}

test("tablo olusturunca listeye eklenir ve basari bildirimi gosterilir", async () => {
  mockFetchSequence([
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [] },
    {
      ok: true,
      status: 201,
      json: async () => ({ id: 1, name: "kullanicilar", kolonlar: [] }),
    },
    {
      ok: true,
      status: 200,
      json: async () => [{ id: 1, name: "kullanicilar", kolonlar: [] }],
    },
  ]);

  renderDashboard();

  await waitFor(() => expect(screen.getByText(/henuz tablo yok/i)).toBeInTheDocument());

  fireEvent.click(screen.getByText("+ Yeni Tablo"));
  fireEvent.change(screen.getByPlaceholderText("tablo_adi"), {
    target: { value: "kullanicilar" },
  });
  fireEvent.click(screen.getByText("Olustur"));

  await waitFor(() =>
    expect(screen.getByText(/olusturuldu/i)).toBeInTheDocument()
  );
  expect(screen.getAllByText("kullanicilar").length).toBeGreaterThan(0);
});

test("backend conflict (409) hatasinda turuncu bildirim gosterir", async () => {
  mockFetchSequence([
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [] },
    {
      ok: false,
      status: 409,
      json: async () => ({
        timestamp: "2026-01-01T00:00:00Z",
        status: 409,
        error: "Conflict",
        message: "tablo adi zaten kullaniliyor",
      }),
    },
  ]);

  renderDashboard();

  await waitFor(() => expect(screen.getByText(/henuz tablo yok/i)).toBeInTheDocument());

  fireEvent.click(screen.getByText("+ Yeni Tablo"));
  fireEvent.change(screen.getByPlaceholderText("tablo_adi"), {
    target: { value: "kullanicilar" },
  });
  fireEvent.click(screen.getByText("Olustur"));

  const notification = await screen.findByText(/tablo adi zaten kullaniliyor/i);
  expect(notification).toHaveClass("notification-conflict");
});
