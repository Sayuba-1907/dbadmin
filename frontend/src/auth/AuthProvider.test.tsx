import "@testing-library/jest-dom";
// AuthProvider useTranslation() kullanir (oturum suresi dolunca bildirim metni icin) —
// import edilmezse react-i18next konsola uyari basar ve t() ham key'i dondurur.
import "../i18n";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AuthProvider, useAuth } from "./AuthProvider";
import { NotificationProvider } from "../notifications/NotificationProvider";

const STORAGE_KEY = "dbadmin-token";

afterEach(() => {
  jest.restoreAllMocks();
  localStorage.clear();
});

function mockFetchOnce(response: Partial<Response>) {
  global.fetch = jest.fn().mockResolvedValue(response) as unknown as typeof fetch;
}

/** AuthProvider'in durumunu ekrana yazan ve login/logout tetikleyen kucuk bir prob component. */
function AuthProbe() {
  const { status, username, role, canWrite, login, logout } = useAuth();
  return (
    <div>
      <p>status:{status}</p>
      <p>kullanici:{username ?? "yok"}</p>
      <p>role:{role ?? "yok"}</p>
      <p>canWrite:{String(canWrite)}</p>
      <button onClick={() => login("admin", "admin123")}>giris-yap</button>
      <button onClick={logout}>cikis-yap</button>
    </div>
  );
}

function renderProbe() {
  return render(
    <NotificationProvider>
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>
    </NotificationProvider>
  );
}

test("localStorage'da token yoksa status anonymous olur", async () => {
  renderProbe();

  await waitFor(() => expect(screen.getByText("status:anonymous")).toBeInTheDocument());
});

test("basarili login sonrasi status authenticated olur ve token localStorage'a yazilir", async () => {
  mockFetchOnce({
    ok: true,
    status: 200,
    json: async () => ({ token: "yeni-jwt", username: "admin", role: "ADMIN" }),
  });

  renderProbe();
  await waitFor(() => expect(screen.getByText("status:anonymous")).toBeInTheDocument());

  fireEvent.click(screen.getByText("giris-yap"));

  await waitFor(() => expect(screen.getByText("status:authenticated")).toBeInTheDocument());
  expect(screen.getByText("kullanici:admin")).toBeInTheDocument();
  expect(screen.getByText("role:ADMIN")).toBeInTheDocument();
  expect(screen.getByText("canWrite:true")).toBeInTheDocument();
  expect(localStorage.getItem(STORAGE_KEY)).toBe("yeni-jwt");
});

test("localStorage'da gecerli bir token varsa acilista /api/auth/me ile onaylanir", async () => {
  localStorage.setItem(STORAGE_KEY, "onceden-kaydedilmis-token");
  mockFetchOnce({
    ok: true,
    status: 200,
    json: async () => ({ token: null, username: "ayse", role: "VIEWER" }),
  });

  renderProbe();

  await waitFor(() => expect(screen.getByText("status:authenticated")).toBeInTheDocument());
  expect(screen.getByText("kullanici:ayse")).toBeInTheDocument();
  // VIEWER: canWrite false olmali — role tabanli buton gizlemenin dayandigi temel kural.
  expect(screen.getByText("canWrite:false")).toBeInTheDocument();
});

test("localStorage'daki token gecersizse (me() 401 doner) anonymous'a duser ve token silinir", async () => {
  localStorage.setItem(STORAGE_KEY, "suresi-dolmus-token");
  mockFetchOnce({
    ok: false,
    status: 401,
    json: async () => ({
      timestamp: "2026-01-01T00:00:00Z",
      status: 401,
      error: "Unauthorized",
      message: "authentication is required, send a valid Bearer token",
      code: "AUTH_REQUIRED",
    }),
  });

  renderProbe();

  await waitFor(() => expect(screen.getByText("status:anonymous")).toBeInTheDocument());
  expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
});

test("logout token'i ve state'i temizler", async () => {
  mockFetchOnce({
    ok: true,
    status: 200,
    json: async () => ({ token: "yeni-jwt", username: "admin", role: "ADMIN" }),
  });
  renderProbe();
  await waitFor(() => expect(screen.getByText("status:anonymous")).toBeInTheDocument());
  fireEvent.click(screen.getByText("giris-yap"));
  await waitFor(() => expect(screen.getByText("status:authenticated")).toBeInTheDocument());

  fireEvent.click(screen.getByText("cikis-yap"));

  expect(screen.getByText("status:anonymous")).toBeInTheDocument();
  expect(screen.getByText("kullanici:yok")).toBeInTheDocument();
  expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
});
