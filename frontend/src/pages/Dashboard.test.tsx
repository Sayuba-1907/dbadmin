import "@testing-library/jest-dom";
// i18next kurulumu burada elle import ediliyor cunku testler index.tsx'i hic calistirmaz
// (o normalde bunu yapan yerdi); import edilmezse t("sidebar.empty") gibi cagrilar ceviri
// yerine ham key string'ini ("sidebar.empty") render eder ve testler o ham key'i arar bulamaz.
import "../i18n";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Dashboard } from "./Dashboard";
import { NotificationProvider } from "../notifications/NotificationProvider";
import { AuthContext, AuthContextValue } from "../auth/AuthProvider";

afterEach(() => {
  jest.restoreAllMocks();
});

function mockFetchSequence(responses: Partial<Response>[]) {
  const fn = jest.fn();
  responses.forEach((response) => fn.mockResolvedValueOnce(response));
  global.fetch = fn as unknown as typeof fetch;
  return fn;
}

/**
 * Bu dosyadaki testler yazma islemlerini (tablo olusturma, surukle-birak) sinadigi icin sabit
 * bir EDITOR baglami veriyoruz — gercek AuthProvider'in localStorage okuyup `/api/auth/ben`'e
 * gitmesini beklemek gereksiz bir asenkron adim (ve mockFetchSequence'teki cagri sirasini
 * kaydirirdi). Rol bazli gizlemeyi (VIEWER'in yazma butonlarini gormemesi) test etmek
 * istenirse ayri bir testte rol: "VIEWER" ile ayni yardimci kullanilabilir.
 */
const EDITOR_AUTH: AuthContextValue = {
  status: "authenticated",
  kullaniciAdi: "test_kullanici",
  rol: "EDITOR",
  canWrite: true,
  isAdmin: false,
  login: jest.fn(),
  logout: jest.fn(),
};

function renderDashboard(auth: AuthContextValue = EDITOR_AUTH) {
  return render(
    <NotificationProvider>
      <AuthContext.Provider value={auth}>
        <Dashboard />
      </AuthContext.Provider>
    </NotificationProvider>
  );
}

test("tablo olusturunca listeye eklenir ve basari bildirimi gosterilir", async () => {
  mockFetchSequence([
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [{ id: 1, name: "kayitlar" }] },
    {
      ok: true,
      status: 201,
      json: async () => ({
        id: 1,
        name: "kullanicilar",
        schemaId: 1,
        schemaName: "kayitlar",
        kolonlar: [],
      }),
    },
    {
      ok: true,
      status: 200,
      json: async () => [
        { id: 1, name: "kullanicilar", schemaId: 1, schemaName: "kayitlar", kolonlar: [] },
      ],
    },
  ]);

  renderDashboard();

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  fireEvent.click(screen.getByText("+ Yeni Tablo"));
  fireEvent.change(screen.getByPlaceholderText("tablo_adi"), {
    target: { value: "kullanicilar" },
  });
  fireEvent.click(screen.getByText("Oluştur"));

  await waitFor(() => expect(screen.getByText(/oluşturuldu/i)).toBeInTheDocument());
  // Tablo adi artik TabloDetail'de duzenlenebilir bir input (text node degil, "value" attribute'u)
  // — getByText bunu bulamaz, getByDisplayValue input/textarea degerine gore arar.
  await waitFor(() => expect(screen.getByDisplayValue("kullanicilar")).toBeInTheDocument());
});

test("backend conflict (409) hatasinda turuncu bildirim gosterir", async () => {
  mockFetchSequence([
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [{ id: 1, name: "kayitlar" }] },
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

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  fireEvent.click(screen.getByText("+ Yeni Tablo"));
  fireEvent.change(screen.getByPlaceholderText("tablo_adi"), {
    target: { value: "kullanicilar" },
  });
  fireEvent.click(screen.getByText("Oluştur"));

  const notificationText = await screen.findByText(/tablo adi zaten kullaniliyor/i);
  expect(notificationText.closest('[role="alert"]')).toHaveClass("notification-conflict");
});

test("backend'in gonderdigi hata kodu, ham Ingilizce mesaj yerine cevrilmis Turkce metni gosterir", async () => {
  mockFetchSequence([
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [{ id: 1, name: "kayitlar" }] },
    {
      ok: false,
      status: 409,
      json: async () => ({
        timestamp: "2026-01-01T00:00:00Z",
        status: 409,
        error: "Conflict",
        message: "a table named 'kullanicilar' already exists",
        code: "CONFLICT_DUPLICATE_TABLE_NAME",
        details: { name: "kullanicilar" },
      }),
    },
  ]);

  renderDashboard();

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  fireEvent.click(screen.getByText("+ Yeni Tablo"));
  fireEvent.change(screen.getByPlaceholderText("tablo_adi"), {
    target: { value: "kullanicilar" },
  });
  fireEvent.click(screen.getByText("Oluştur"));

  const notificationText = await screen.findByText('"kullanicilar" adında bir tablo zaten var');
  expect(notificationText.closest('[role="alert"]')).toHaveClass("notification-conflict");
  expect(screen.queryByText(/already exists/i)).not.toBeInTheDocument();
});

test("taninmayan bir hata kodu gelirse backend'in ham mesajina dusulur", async () => {
  mockFetchSequence([
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [{ id: 1, name: "kayitlar" }] },
    {
      ok: false,
      status: 409,
      json: async () => ({
        timestamp: "2026-01-01T00:00:00Z",
        status: 409,
        error: "Conflict",
        message: "some future backend error not yet translated",
        code: "SOME_FUTURE_CODE",
        details: {},
      }),
    },
  ]);

  renderDashboard();

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  fireEvent.click(screen.getByText("+ Yeni Tablo"));
  fireEvent.change(screen.getByPlaceholderText("tablo_adi"), {
    target: { value: "kullanicilar" },
  });
  fireEvent.click(screen.getByText("Oluştur"));

  const notificationText = await screen.findByText("some future backend error not yet translated");
  expect(notificationText.closest('[role="alert"]')).toHaveClass("notification-conflict");
});

test("tabloyu surukleyip baska schema'nin uzerine birakinca o schema'ya tasir", async () => {
  const fetchMock = mockFetchSequence([
    {
      ok: true,
      status: 200,
      json: async () => [
        { id: 10, name: "kullanicilar", schemaId: 1, schemaName: "kayitlar", kolonlar: [] },
      ],
    },
    { ok: true, status: 200, json: async () => [] },
    {
      ok: true,
      status: 200,
      json: async () => [
        { id: 1, name: "kayitlar" },
        { id: 2, name: "ogrenciler" },
      ],
    },
    {
      ok: true,
      status: 200,
      json: async () => ({
        id: 10,
        name: "kullanicilar",
        schemaId: 2,
        schemaName: "ogrenciler",
        kolonlar: [],
      }),
    },
    {
      ok: true,
      status: 200,
      json: async () => [
        { id: 10, name: "kullanicilar", schemaId: 2, schemaName: "ogrenciler", kolonlar: [] },
      ],
    },
  ]);

  renderDashboard();

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  // Schema varsayilan kapali baslar; icindeki tabloyu gormek icin acmak lazim.
  fireEvent.click(screen.getByText("kayitlar"));
  const tableItem = await screen.findByText("kullanicilar");
  const targetLi = screen.getByText("ogrenciler").closest("li");
  expect(targetLi).not.toBeNull();

  fireEvent.dragStart(tableItem);
  fireEvent.dragOver(targetLi as HTMLElement);
  fireEvent.drop(targetLi as HTMLElement);

  await waitFor(() =>
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/tablolar/10/schema"),
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ schemaId: 2 }) })
    )
  );
});

test("VIEWER rolunde yazma butonlari devre disi kalir", async () => {
  mockFetchSequence([
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [{ id: 1, name: "kayitlar" }] },
  ]);

  renderDashboard({ ...EDITOR_AUTH, rol: "VIEWER", canWrite: false });

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  // Butonlar hala GORUNUYOR (VIEWER ne yapabilecegini gorebilsin) ama tiklanamaz durumda —
  // backend zaten 403 doner (SecurityConfig), bu sadece bosuna bir istek atilmasini onluyor.
  expect(screen.getByText("+ Yeni Tablo")).toBeDisabled();
  expect(screen.getByText("+ Yeni Schema")).toBeDisabled();
});

test("ADMIN icin acilista kullanici listesi de cekilir ve Kullanicilar sekmesinde gosterilir", async () => {
  mockFetchSequence([
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [] },
    { ok: true, status: 200, json: async () => [{ id: 1, name: "kayitlar" }] },
    {
      ok: true,
      status: 200,
      json: async () => [
        { id: 1, kullaniciAdi: "admin", rol: "ADMIN" },
        { id: 2, kullaniciAdi: "ayse", rol: "VIEWER" },
      ],
    },
  ]);

  renderDashboard({ ...EDITOR_AUTH, rol: "ADMIN", isAdmin: true });

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  fireEvent.click(screen.getByText("Kullanıcılar"));

  expect(await screen.findByText("ayse")).toBeInTheDocument();
  expect(screen.getByText("admin")).toBeInTheDocument();
});
