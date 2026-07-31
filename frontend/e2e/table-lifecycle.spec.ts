import { test, expect } from "@playwright/test";

/**
 * Tam bir kullanici senaryosu: tablo olustur -> kolon ekle -> kolonu sil (geri alinabilir
 * bildirimle) -> "Geri Al"a bas -> kolonun gercekten geri geldigini dogrula -> en sonda
 * tum test tablosunu gercekten silip temizlik yap.
 *
 * Jest/RTL testlerinden farki: burada fetch mock'lanmiyor, gercek backend'e gercek istekler
 * gidiyor, ve native `window.confirm()` diyaloglari da gercekten test ediliyor (Jest/RTL
 * ortaminda bunu hic test edemiyorduk).
 *
 * Ayni backend'i paylasan baska testlerle/kullanicilarla isim catismasin diye tablo adi
 * her calistirmada `Date.now()` ile benzersiz uretiliyor (NameValidator'in kurallarina uysun
 * diye kucuk harfle basliyor, sadece harf/rakam/alt cizgi iceriyor).
 */
test("tablo olustur, kolon ekle, kolonu sil ve geri al, sonra tabloyu temizle", async ({
  page,
}) => {
  test.setTimeout(90000);

  const tableName = `e2e_${Date.now()}`;

  await page.goto("/");

  // 1) Yeni tablo olustur.
  await page.getByRole("button", { name: "+ Yeni Tablo" }).click();
  await page.getByPlaceholder("tablo_adi").fill(tableName);
  await page.getByPlaceholder("kolon_adi").first().fill("ad");
  await page.getByRole("button", { name: "Oluştur" }).click();

  await expect(page.getByText(`"${tableName}" tablosu oluşturuldu`)).toBeVisible();
  // Yeni tablo otomatik secilir; basligin gercekten bu tablo oldugunu dogrula.
  await expect(page.getByRole("heading", { name: tableName })).toBeVisible();

  // 2) Ikinci bir kolon ekle.
  await page.getByPlaceholder("kolon_adi").fill("yas");
  await page.getByRole("button", { name: "Kolon Ekle" }).click();
  await expect(page.getByText('"yas" kolonu eklendi')).toBeVisible();

  const yasRow = page.locator("tr", { hasText: "yas" });
  await expect(yasRow).toBeVisible();

  // 3) O kolonu sil — native confirm() diyalogunu Playwright'in kendi dialog API'siyle kabul et.
  page.once("dialog", (dialog) => dialog.accept());
  await yasRow.getByRole("button", { name: "Sil" }).click();

  // 4) "Geri Al" bildirimi cikar, hemen tikla.
  const undoButton = page.getByRole("button", { name: "Geri Al" });
  await expect(undoButton).toBeVisible();
  await undoButton.click();

  // 5) Kolon hemen geri gelmeli...
  await expect(page.locator("tr", { hasText: "yas" })).toBeVisible();

  // ...ve geri alma penceresi (5sn) gectikten sonra da HALA orada olmali — yani gercek DELETE
  // istegi hic gitmemis. Bu, "geri alma" ozelliginin sahte degil gercek oldugunu kanitlayan asil satir.
  await page.waitForTimeout(6000);
  await expect(page.locator("tr", { hasText: "yas" })).toBeVisible();

  // 6) Temizlik: butun test tablosunu gercekten sil (bu sefer "Geri Al"a basmadan, gercekten gitsin).
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Tabloyu Sil" }).click();
  await expect(page.getByText("Tablo silindi")).toBeVisible();
  await page.waitForTimeout(6000);

  await page.reload();
  await expect(page.getByRole("button", { name: tableName })).toHaveCount(0);
});
