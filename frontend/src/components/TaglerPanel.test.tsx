import "@testing-library/jest-dom";
// Dashboard.test.tsx'teki ile ayni gerekce: testler index.tsx'i calistirmaz, o yuzden
// i18next kurulumu elle import edilmezse t("tagler.title") ceviri yerine ham key doner.
import "../i18n";
import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { TaglerPanel } from "./TaglerPanel";
import { KolonUsage, Tag } from "../api/tags";

const TAGS: Tag[] = [
  { id: 1, name: "kimlik" },
  { id: 2, name: "iletisim" },
];

const KIMLIK_USAGE: KolonUsage[] = [
  {
    tabloId: 10,
    tabloName: "ogrenciler",
    schemaName: "ders_sema",
    kolonId: 100,
    kolonName: "tc_no",
  },
  { tabloId: 11, tabloName: "personel", schemaName: "ik_sema", kolonId: 101, kolonName: "sicil" },
];

/**
 * Bir etiketin kendi satirindaki butonu dondurur. Ekranda birden fazla "Ayrıntı" butonu
 * oldugu icin rol+isim ile aramak yetmiyor — once etiketin <li>'sine inip orada ariyoruz.
 */
function detailButton(tagName: string) {
  const row = screen.getByText(tagName).closest("li") as HTMLElement;
  return within(row).getByRole("button");
}

test("etiketler listelenir ve hicbiri acilmadan kullanim istegi atilmaz", () => {
  const onLoadUsage = jest.fn();
  render(<TaglerPanel tags={TAGS} onLoadUsage={onLoadUsage} />);

  expect(screen.getByText("kimlik")).toBeInTheDocument();
  expect(screen.getByText("iletisim")).toBeInTheDocument();
  // Kullanim verisi lazy cekiliyor: panel acilir acilmaz degil, sadece "Ayrıntı"ya basilinca.
  expect(onLoadUsage).not.toHaveBeenCalled();
});

test("hic etiket yoksa bos liste metni gosterilir", () => {
  render(<TaglerPanel tags={[]} onLoadUsage={jest.fn()} />);

  expect(screen.getByText("Henüz hiç etiket yok")).toBeInTheDocument();
});

test("Ayrıntı'ya basinca etiketi kullanan kolonlar schema.tablo.kolon seklinde acilir", async () => {
  const onLoadUsage = jest.fn().mockResolvedValue(KIMLIK_USAGE);
  render(<TaglerPanel tags={TAGS} onLoadUsage={onLoadUsage} />);

  fireEvent.click(detailButton("kimlik"));

  expect(await screen.findByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();
  expect(screen.getByText("ik_sema.personel.sicil")).toBeInTheDocument();
  // Istek acilan etiketin id'siyle atilmali, listedeki sirasiyla degil.
  expect(onLoadUsage).toHaveBeenCalledWith(1);
});

test("kullanilmayan bir etiket icin bos liste yerine aciklayici metin gosterilir", async () => {
  const onLoadUsage = jest.fn().mockResolvedValue([]);
  render(<TaglerPanel tags={TAGS} onLoadUsage={onLoadUsage} />);

  fireEvent.click(detailButton("kimlik"));

  expect(await screen.findByText("Bu etiket hiçbir kolonda kullanılmıyor")).toBeInTheDocument();
});

test("ayni etiketi kapatip tekrar acmak ikinci bir istek atmaz (onbellek)", async () => {
  const onLoadUsage = jest.fn().mockResolvedValue(KIMLIK_USAGE);
  render(<TaglerPanel tags={TAGS} onLoadUsage={onLoadUsage} />);

  fireEvent.click(detailButton("kimlik"));
  expect(await screen.findByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();
  expect(detailButton("kimlik")).toHaveTextContent("Gizle");

  fireEvent.click(detailButton("kimlik")); // kapat
  expect(screen.queryByText("ders_sema.ogrenciler.tc_no")).not.toBeInTheDocument();

  fireEvent.click(detailButton("kimlik")); // tekrar ac
  expect(await screen.findByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();
  // Ikinci acilis onbellekten gelmeli — istek hala tek.
  expect(onLoadUsage).toHaveBeenCalledTimes(1);
});

test("baska bir etiket acilinca oncekinin ayrintisi kapanir (accordion)", async () => {
  const onLoadUsage = jest.fn(async (tagId: number) => (tagId === 1 ? KIMLIK_USAGE : []));
  render(<TaglerPanel tags={TAGS} onLoadUsage={onLoadUsage} />);

  fireEvent.click(detailButton("kimlik"));
  expect(await screen.findByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();

  fireEvent.click(detailButton("iletisim"));

  expect(await screen.findByText("Bu etiket hiçbir kolonda kullanılmıyor")).toBeInTheDocument();
  expect(screen.queryByText("ders_sema.ogrenciler.tc_no")).not.toBeInTheDocument();
});

test("istek surerken yukleniyor metni gosterilir, cevap gelince yerini listeye birakir", async () => {
  // Promise'i bilerek elde tutuyoruz: boylece "istek surerken" anini gozlemleyebiliyoruz,
  // normalde mockResolvedValue ile bu ara durum bir microtask'ta gecip gorunmez olurdu.
  let resolveUsage!: (usage: KolonUsage[]) => void;
  const onLoadUsage = jest.fn(
    () => new Promise<KolonUsage[]>((resolve) => (resolveUsage = resolve))
  );
  render(<TaglerPanel tags={TAGS} onLoadUsage={onLoadUsage} />);

  fireEvent.click(detailButton("kimlik"));
  expect(screen.getByText("Yükleniyor...")).toBeInTheDocument();

  await act(async () => resolveUsage(KIMLIK_USAGE));

  expect(screen.queryByText("Yükleniyor...")).not.toBeInTheDocument();
  expect(screen.getByText("ders_sema.ogrenciler.tc_no")).toBeInTheDocument();
});
