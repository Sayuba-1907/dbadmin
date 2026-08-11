import "@testing-library/jest-dom";
// Dashboard.test.tsx'teki ile ayni gerekce: testler index.tsx'i calistirmaz, o yuzden
// i18next kurulumu elle import edilmezse t("tagler.title") ceviri yerine ham key doner.
import "../i18n";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { PrimeReactProvider } from "primereact/api";
import { TagsPanel } from "./TagsPanel";
import { ColumnUsage, Tag } from "../api/tags";
import { AuthContext, AuthContextValue } from "../auth/AuthProvider";
import { ConfirmProvider } from "../notifications/ConfirmProvider";

const TAGS: Tag[] = [
  { id: 1, name: "kimlik" },
  { id: 2, name: "iletisim" },
];

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

/** TagsPanel useAuth() kullaniyor (canWrite icin), o yuzden her testte AuthContext saglanmali. */
function renderPanel(props: {
  tags: Tag[];
  onLoadUsage: (tagId: number) => Promise<ColumnUsage[]>;
  onRename?: (tagId: number, name: string) => void;
  onDelete?: (tagId: number) => void;
}) {
  return render(
    <PrimeReactProvider value={{ unstyled: true }}>
      <ConfirmProvider>
        <AuthContext.Provider value={EDITOR_AUTH}>
          <TagsPanel
            tags={props.tags}
            onLoadUsage={props.onLoadUsage}
            onRename={props.onRename ?? jest.fn()}
            onDelete={props.onDelete ?? jest.fn()}
          />
        </AuthContext.Provider>
      </ConfirmProvider>
    </PrimeReactProvider>
  );
}

const KIMLIK_USAGE: ColumnUsage[] = [
  {
    tableId: 10,
    tableName: "ogrenciler",
    schemaName: "ders_sema",
    columnId: 100,
    columnName: "tc_no",
  },
  { tableId: 11, tableName: "personel", schemaName: "ik_sema", columnId: 101, columnName: "sicil" },
];

/**
 * Artik ayri bir "Ayrıntı" butonu yok — tag'in ismine tiklamak ayrinti panelini ac/kapa
 * yapiyor (bkz. TagsPanel.tsx, role="button" olan .tagler-name span'i).
 */
function detailButton(tagName: string) {
  return screen.getByText(tagName, { selector: ".tagler-name" });
}

test("etiketler listelenir ve hicbiri acilmadan kullanim istegi atilmaz", () => {
  const onLoadUsage = jest.fn();
  renderPanel({ tags: TAGS, onLoadUsage });

  expect(screen.getByText("kimlik")).toBeInTheDocument();
  expect(screen.getByText("iletisim")).toBeInTheDocument();
  // Kullanim verisi lazy cekiliyor: panel acilir acilmaz degil, sadece "Ayrıntı"ya basilinca.
  expect(onLoadUsage).not.toHaveBeenCalled();
});

test("hic etiket yoksa bos liste metni gosterilir", () => {
  renderPanel({ tags: [], onLoadUsage: jest.fn() });

  expect(screen.getByText("Henüz hiç etiket yok")).toBeInTheDocument();
});

test("Ayrıntı'ya basinca etiketi kullanan columns schema.tablo.kolon seklinde acilir", async () => {
  const onLoadUsage = jest.fn().mockResolvedValue(KIMLIK_USAGE);
  renderPanel({ tags: TAGS, onLoadUsage });

  fireEvent.click(detailButton("kimlik"));

  expect(await screen.findByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();
  expect(screen.getByText("ik_sema.personel.sicil")).toBeInTheDocument();
  // Istek acilan etiketin id'siyle atilmali, listedeki sirasiyla degil.
  expect(onLoadUsage).toHaveBeenCalledWith(1);
});

test("kullanilmayan bir etiket icin bos liste yerine aciklayici metin gosterilir", async () => {
  const onLoadUsage = jest.fn().mockResolvedValue([]);
  renderPanel({ tags: TAGS, onLoadUsage });

  fireEvent.click(detailButton("kimlik"));

  expect(await screen.findByText("Bu etiket hiçbir kolonda kullanılmıyor")).toBeInTheDocument();
});

test("ayni etiketi kapatip tekrar acmak ikinci bir istek atmaz (onbellek)", async () => {
  const onLoadUsage = jest.fn().mockResolvedValue(KIMLIK_USAGE);
  renderPanel({ tags: TAGS, onLoadUsage });

  fireEvent.click(detailButton("kimlik"));
  expect(await screen.findByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();

  fireEvent.click(detailButton("kimlik")); // kapat
  expect(screen.queryByText("ders_sema.ogrenciler.tc_no")).not.toBeInTheDocument();

  fireEvent.click(detailButton("kimlik")); // tekrar ac
  expect(await screen.findByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();
  // Ikinci acilis onbellekten gelmeli — istek hala tek.
  expect(onLoadUsage).toHaveBeenCalledTimes(1);
});

test("baska bir etiket acilinca oncekinin ayrintisi acik kalir (birden fazla ayni anda acilabilir)", async () => {
  const onLoadUsage = jest.fn(async (tagId: number) => (tagId === 1 ? KIMLIK_USAGE : []));
  renderPanel({ tags: TAGS, onLoadUsage });

  fireEvent.click(detailButton("kimlik"));
  expect(await screen.findByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();

  fireEvent.click(detailButton("iletisim"));

  expect(await screen.findByText("Bu etiket hiçbir kolonda kullanılmıyor")).toBeInTheDocument();
  // TableSidebar'daki schema acilir/kapanir deseniyle ayni: "iletisim" acilinca "kimlik"
  // kapanmiyor, ikisi de ayni anda ekranda kaliyor.
  expect(screen.getByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();
});

test("istek surerken yukleniyor durumu gosterilir, cevap gelince yerini listeye birakir", async () => {
  // Promise'i bilerek elde tutuyoruz: boylece "istek surerken" anini gozlemleyebiliyoruz,
  // normalde mockResolvedValue ile bu ara durum bir microtask'ta gecip gorunmez olurdu.
  let resolveUsage!: (usage: ColumnUsage[]) => void;
  const onLoadUsage = jest.fn(
    () => new Promise<ColumnUsage[]>((resolve) => (resolveUsage = resolve))
  );
  renderPanel({ tags: TAGS, onLoadUsage });

  fireEvent.click(detailButton("kimlik"));
  expect(screen.getByRole("status", { name: "Yükleniyor..." })).toBeInTheDocument();

  await act(async () => resolveUsage(KIMLIK_USAGE));

  expect(screen.queryByRole("status", { name: "Yükleniyor..." })).not.toBeInTheDocument();
  expect(screen.getByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();
});

test("Düzenle ile isim degistirilip Kaydet'e basilinca onRename yeni isimle cagrilir", () => {
  const onRename = jest.fn();
  renderPanel({ tags: TAGS, onLoadUsage: jest.fn(), onRename });

  const row = screen.getByText("kimlik").closest("li") as HTMLElement;
  fireEvent.click(within(row).getByRole("button", { name: "Düzenle" }));

  const input = within(row).getByDisplayValue("kimlik");
  fireEvent.change(input, { target: { value: "kimlik_v2" } });
  fireEvent.click(within(row).getByRole("button", { name: "Kaydet" }));

  expect(onRename).toHaveBeenCalledWith(1, "kimlik_v2");
});

test("Sil'e basip onaylayinca onDelete cagrilir, onaylanmazsa cagrilmaz", async () => {
  const onDelete = jest.fn();
  renderPanel({ tags: TAGS, onLoadUsage: jest.fn(), onDelete });

  const row = screen.getByText("kimlik").closest("li") as HTMLElement;
  const silButton = within(row).getByRole("button", { name: "Sil" });

  // window.confirm yerine ConfirmProvider'in Promise tabanli ozel modali (bkz.
  // notifications/ConfirmProvider.tsx) — "Sil" hem satirdaki butonun hem modaldeki onay
  // butonunun adi oldugu icin modal icindeki butonu within(modal) ile ayirt ediyoruz.
  fireEvent.click(silButton);
  const cancelBtn = await screen.findByRole("button", { name: "Vazgeç" });
  fireEvent.click(cancelBtn); // Iptal -> silinmemeli
  expect(onDelete).not.toHaveBeenCalled();
  // PrimeReact'in ConfirmDialog'u kapanirken bir CSS gecis animasyonu oynatiyor (bkz.
  // confirm-modal-in/exit) — element tiklamadan hemen sonra degil, o gecis bitince DOM'dan
  // kalkiyor, o yuzden anlik queryBy* degil waitFor gerekiyor.
  await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());

  fireEvent.click(silButton);
  const modal = await screen.findByRole("alertdialog");
  fireEvent.click(within(modal).getByRole("button", { name: "Sil" })); // onayla -> silinmeli
  await waitFor(() => expect(onDelete).toHaveBeenCalledWith(1));
});
