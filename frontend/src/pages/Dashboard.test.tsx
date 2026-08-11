import "@testing-library/jest-dom";
// i18next kurulumu burada elle import ediliyor cunku testler index.tsx'i hic calistirmaz
// (o normalde bunu yapan yerdi); import edilmezse t("sidebar.empty") gibi cagrilar ceviri
// yerine ham key string'ini ("sidebar.empty") render eder ve testler o ham key'i arar bulamaz.
import "../i18n";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PrimeReactProvider } from "primereact/api";
import { Dashboard } from "./Dashboard";
import { NotificationProvider } from "../notifications/NotificationProvider";
import { ConfirmProvider } from "../notifications/ConfirmProvider";
import { AuthContext, AuthContextValue } from "../auth/AuthProvider";

afterEach(() => {
  jest.restoreAllMocks();
});

interface FakeKolon {
  id: number;
  name: string;
  type: string;
  tagId: number | null;
  tagName: string | null;
  primaryKey: boolean;
}

interface FakeSchema {
  id: number;
  name: string;
}

interface FakeTablo {
  id: number;
  name: string;
  schemaId: number;
  schemaName: string;
  columns: FakeKolon[];
}

interface MockResponse {
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
}

interface MockOverride {
  method: string;
  path: string | RegExp;
  response: MockResponse;
}

/**
 * Dashboard, schema/tablo agacini mount'ta TEK istekte (GET /api/schemas/schemaList) cekiyor;
 * tiklanan tablonun tam detayi ayrica id'siyle, tags/kullanicilar ise sadece o sekmeye girilince
 * cekiliyor. Sabit index'li bir mockFetchSequence bu yuzden kirilgan; onun yerine URL/method'a
 * gore yanit ureten, gercek backend gibi davranan (create/PATCH ile kendi state'ini guncelleyen)
 * kucuk bir sahte backend kullaniyoruz. Boylece testler "hangi istek kacinci sirada" gibi
 * uygulama detaylarina degil, sadece davranisa bagli kaliyor.
 */
function createFakeBackend(seed: {
  schemalar?: FakeSchema[];
  tablolar?: FakeTablo[];
  kullanicilar?: unknown[];
  overrides?: MockOverride[];
}) {
  const schemalar = seed.schemalar ? [...seed.schemalar] : [];
  const tablolar = seed.tablolar ? [...seed.tablolar] : [];
  const kullanicilar = seed.kullanicilar ? [...seed.kullanicilar] : [];
  const overrides = seed.overrides ? [...seed.overrides] : [];
  let nextTabloId = tablolar.reduce((max, t) => Math.max(max, t.id), 0) + 1;
  let nextKolonId =
    tablolar.flatMap((t) => t.columns).reduce((max, k) => Math.max(max, k.id), 0) + 1;

  const schemaResponse = (s: FakeSchema) => ({
    id: s.id,
    name: s.name,
    tableCount: tablolar.filter((t) => t.schemaId === s.id).length,
  });
  const tabloSummary = (t: FakeTablo) => ({
    id: t.id,
    name: t.name,
    columnCount: t.columns.length,
  });
  /** GET /api/schemas/schemaList — backend'in SchemaResponseDTO/TableSummaryDTO sekli. */
  const workspaceResponse = () =>
    schemalar.map((s) => ({
      schemaId: s.id,
      schemaName: s.name,
      tableResponseList: tablolar
        .filter((t) => t.schemaId === s.id)
        .map((t) => ({
          id: t.id,
          name: t.name,
          columnCount: t.columns.length,
          schemaId: t.schemaId,
        })),
    }));
  const tabloResponse = (t: FakeTablo) => ({
    id: t.id,
    name: t.name,
    schemaId: t.schemaId,
    schemaName: t.schemaName,
    columns: t.columns,
    updatedAt: null,
  });

  const fn = jest.fn((rawUrl: string, options?: RequestInit): Promise<MockResponse> => {
    const method = (options?.method ?? "GET") as string;
    const path = rawUrl.replace("http://localhost:8081", "");

    const overrideIndex = overrides.findIndex(
      (o) =>
        o.method === method && (typeof o.path === "string" ? o.path === path : o.path.test(path))
    );
    if (overrideIndex !== -1) {
      const [override] = overrides.splice(overrideIndex, 1);
      return Promise.resolve(override.response);
    }

    if (method === "GET" && path.startsWith("/api/schemas?")) {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({ content: schemalar.map(schemaResponse) }),
      });
    }
    if (method === "GET" && path === "/api/schemas/schemaList") {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => workspaceResponse(),
      });
    }
    let match = path.match(/^\/api\/schemalar\/(\d+)\/tablolar$/);
    if (method === "GET" && match) {
      const schemaId = Number(match[1]);
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => tablolar.filter((t) => t.schemaId === schemaId).map(tabloSummary),
      });
    }
    match = path.match(/^\/api\/tablolar\/(\d+)$/);
    if (method === "GET" && match) {
      const tablo = tablolar.find((t) => t.id === Number(match![1]));
      return Promise.resolve({ ok: true, status: 200, json: async () => tabloResponse(tablo!) });
    }
    if (method === "GET" && path.startsWith("/api/tags?")) {
      return Promise.resolve({ ok: true, status: 200, json: async () => ({ content: [] }) });
    }
    if (method === "GET" && path.startsWith("/api/users?")) {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({ content: kullanicilar }),
      });
    }
    if (method === "POST" && path === "/api/tables") {
      const body = JSON.parse(options!.body as string);
      const schema = schemalar.find((s) => s.id === body.schemaId)!;
      const created: FakeTablo = {
        id: nextTabloId++,
        name: body.name,
        schemaId: schema.id,
        schemaName: schema.name,
        columns: (body.columns ?? []).map(
          (k: { name: string; type: string; tagId?: number | null; primaryKey?: boolean }) => ({
            id: nextKolonId++,
            name: k.name,
            type: k.type,
            tagId: k.tagId ?? null,
            tagName: null,
            primaryKey: !!k.primaryKey,
          })
        ),
      };
      tablolar.push(created);
      return Promise.resolve({ ok: true, status: 201, json: async () => tabloResponse(created) });
    }
    match = path.match(/^\/api\/tablolar\/(\d+)\/schema$/);
    if (method === "PATCH" && match) {
      const body = JSON.parse(options!.body as string);
      const tablo = tablolar.find((t) => t.id === Number(match![1]))!;
      const schema = schemalar.find((s) => s.id === body.schemaId)!;
      tablo.schemaId = schema.id;
      tablo.schemaName = schema.name;
      return Promise.resolve({ ok: true, status: 200, json: async () => tabloResponse(tablo) });
    }

    throw new Error(`createFakeBackend: mock icin tanimsiz istek ${method} ${path}`);
  });

  global.fetch = fn as unknown as typeof fetch;
  return fn;
}

/**
 * Bu dosyadaki testler yazma islemlerini (tablo olusturma, surukle-birak) sinadigi icin sabit
 * bir EDITOR baglami veriyoruz — gercek AuthProvider'in localStorage okuyup `/api/auth/me`'e
 * gitmesini beklemek gereksiz bir asenkron adim. Role bazli gizlemeyi (VIEWER'in yazma
 * butonlarini gormemesi) test etmek istenirse ayri bir testte role: "VIEWER" ile ayni yardimci
 * kullanilabilir.
 */
const EDITOR_AUTH: AuthContextValue = {
  status: "authenticated",
  id: 1,
  username: "test_kullanici",
  fullName: null,
  hasAvatar: false,
  role: "EDITOR",
  canWrite: true,
  isAdmin: false,
  login: jest.fn(),
  logout: jest.fn(),
  applyProfileUpdate: jest.fn(),
};

function renderDashboard(auth: AuthContextValue = EDITOR_AUTH) {
  return render(
    <PrimeReactProvider value={{ unstyled: true }}>
      <NotificationProvider>
        <ConfirmProvider>
          <AuthContext.Provider value={auth}>
            <Dashboard />
          </AuthContext.Provider>
        </ConfirmProvider>
      </NotificationProvider>
    </PrimeReactProvider>
  );
}

test("tablo olusturunca listeye eklenir ve basari bildirimi gosterilir", async () => {
  createFakeBackend({ schemalar: [{ id: 1, name: "kayitlar" }] });

  renderDashboard();

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  fireEvent.click(screen.getByText("+ Yeni Tablo"));
  fireEvent.change(screen.getByPlaceholderText("tablo_adi"), {
    target: { value: "kullanicilar" },
  });
  fireEvent.click(screen.getByText("Oluştur"));

  await waitFor(() => expect(screen.getByText(/oluşturuldu/i)).toBeInTheDocument());
  // Table adi artik TableDetail'de duzenlenebilir bir input (text node degil, "value" attribute'u)
  // — getByText bunu bulamaz, getByDisplayValue input/textarea degerine gore arar.
  await waitFor(() => expect(screen.getByDisplayValue("kullanicilar")).toBeInTheDocument());
});

test("backend conflict (409) hatasinda turuncu bildirim gosterir", async () => {
  createFakeBackend({
    schemalar: [{ id: 1, name: "kayitlar" }],
    overrides: [
      {
        method: "POST",
        path: "/api/tables",
        response: {
          ok: false,
          status: 409,
          json: async () => ({
            timestamp: "2026-01-01T00:00:00Z",
            status: 409,
            error: "Conflict",
            message: "tablo adi zaten kullaniliyor",
          }),
        },
      },
    ],
  });

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
  createFakeBackend({
    schemalar: [{ id: 1, name: "kayitlar" }],
    overrides: [
      {
        method: "POST",
        path: "/api/tables",
        response: {
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
      },
    ],
  });

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
  createFakeBackend({
    schemalar: [{ id: 1, name: "kayitlar" }],
    overrides: [
      {
        method: "POST",
        path: "/api/tables",
        response: {
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
      },
    ],
  });

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
  const fetchMock = createFakeBackend({
    schemalar: [
      { id: 1, name: "kayitlar" },
      { id: 2, name: "ogrenciler" },
    ],
    tablolar: [{ id: 10, name: "kullanicilar", schemaId: 1, schemaName: "kayitlar", columns: [] }],
  });

  renderDashboard();

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  // Schema varsayilan kapali baslar; icindeki tabloyu gormek icin acmak lazim. Isme tiklamak
  // artik genisletmiyor (Tree'nin kendi ayri toggler butonu var, bkz. TableSidebar.tsx) —
  // toggler'i "kayitlar" satirinin en yakin treeitem'i icinde arayip ona tikliyoruz.
  const kayitlarNode = screen.getByText("kayitlar").closest('[role="treeitem"]') as HTMLElement;
  // Toggler'in erisilebilir bir adi yok (sadece aria-hidden bir SVG icon), o yuzden role
  // sorgusu yerine PrimeReact'in kendi kararli data-pc-section niteligiyle buluyoruz.
  fireEvent.click(kayitlarNode.querySelector('[data-pc-section="toggler"]') as HTMLElement);
  const tableItem = await screen.findByText("kullanicilar");
  // Drop handler'lari artik <li> (treeitem) uzerinde degil, onun icindeki schema-header-row
  // div'inde — bkz. TableSidebar.tsx'teki nodeTemplate.
  const targetDropZone = screen.getByText("ogrenciler").closest(".schema-header-row");
  expect(targetDropZone).not.toBeNull();

  fireEvent.dragStart(tableItem);
  fireEvent.dragOver(targetDropZone as HTMLElement);
  fireEvent.drop(targetDropZone as HTMLElement);

  await waitFor(() =>
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/tables/10/schema"),
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ schemaId: 2 }) })
    )
  );
});

test("VIEWER rolunde yazma butonlari hic gosterilmez", async () => {
  createFakeBackend({ schemalar: [{ id: 1, name: "kayitlar" }] });

  renderDashboard({ ...EDITOR_AUTH, role: "VIEWER", canWrite: false });

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  // VIEWER zaten yapamayacagi bir aksiyonu gorup "neden tiklanmiyor" diye ugrasmasin diye
  // bu butonlar gri/disabled degil, DOM'da hic yok (bkz. TableSidebar.tsx).
  expect(screen.queryByText("+ Yeni Tablo")).not.toBeInTheDocument();
  expect(screen.queryByText("+ Yeni Schema")).not.toBeInTheDocument();
});

test("ADMIN, Kullanicilar sekmesine girince kullanici listesi cekilir ve gosterilir", async () => {
  createFakeBackend({
    schemalar: [{ id: 1, name: "kayitlar" }],
    kullanicilar: [
      { id: 1, username: "admin", role: "ADMIN" },
      { id: 2, username: "ayse", role: "VIEWER" },
    ],
  });

  renderDashboard({ ...EDITOR_AUTH, role: "ADMIN", isAdmin: true });

  await waitFor(() => expect(screen.getByText("kayitlar")).toBeInTheDocument());

  fireEvent.click(screen.getByText("Kullanıcılar"));

  expect(await screen.findByText("ayse")).toBeInTheDocument();
  expect(screen.getByText("admin")).toBeInTheDocument();
});
