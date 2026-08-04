import { test, expect, Page } from "@playwright/test";

/**
 * Uygulama artik anonim erisime kapali (bkz. AuthProvider.tsx) — her e2e testi once giris
 * yapmak zorunda, aksi halde LoginPage'de takilir. Playwright'in her testi taze bir tarayici
 * context'inde (bos localStorage ile) baslattigi icin bu adim atlanamaz.
 */
async function login(page: Page) {
  await page.goto("/");
  await page.getByLabel("Kullanıcı adı").fill("admin");
  await page.getByLabel("Parola").fill("admin123");
  await page.getByRole("button", { name: "Giriş Yap" }).click();
  await expect(page.getByRole("button", { name: "+ Yeni Tablo" })).toBeVisible();
}

/**
 * Tam bir kullanici senaryosu: tablo olustur -> ikinci kolon ekle (taslak) -> Kaydet ile
 * gercekten gonder -> o kolonu sil isaretle -> Geri Al ile taslaktaki isareti geri al ->
 * gercekten sil ve Kaydet -> en sonda tum test tablosunu sil ve temizle (silme + reload sonrasi
 * da yok oldugunu dogrula).
 * <p>
 * ONEMLI: TabloDetail'deki kolon ekleme/silme SENKRON'dur — hicbiri API'ye gitmez, sadece
 * Dashboard'daki taslagi (draft) gunceller (bkz. Dashboard.handleAddDraftKolon /
 * handleToggleDeleteDraftKolon). Tek asenkron adim "Kaydet" (onSave): taslak ile orijinal
 * tablo arasindaki farki TEK istekte backend'e gonderir. Yani "Kolon Ekle"ye basinca ne toast
 * cikar ne satir kalici olur — once Kaydet'e basmak gerekir. Bu test o yuzden iki asamali:
 * once taslakta ekle/Geri-Al'i (hicbir istek gitmeden) dogrular, sonra Kaydet ile gercek
 * DELETE'in kalici oldugunu kanitlar.
 * <p>
 * Jest/RTL testlerinden farki: burada fetch mock'lanmiyor, gercek backend'e gercek istekler
 * gidiyor, ve ConfirmProvider'in gercek modal'i da gercekten tiklanarak test ediliyor (Jest/RTL
 * ortaminda bunu hic test edemiyorduk).
 *
 * Ayni backend'i paylasan baska testlerle/kullanicilarla isim catismasin diye tablo adi
 * her calistirmada `Date.now()` ile benzersiz uretiliyor (NameValidator'in kurallarina uysun
 * diye kucuk harfle basliyor, sadece harf/rakam/alt cizgi iceriyor).
 */
test("tablo olustur, kolon ekle/sil ve geri al, sonra tabloyu temizle", async ({ page }) => {
  test.setTimeout(90000);

  const tableName = `e2e_${Date.now()}`;

  await login(page);

  // 1) Yeni tablo olustur (bu, taslak degil — direkt API'ye gider).
  await page.getByRole("button", { name: "+ Yeni Tablo" }).click();
  await page.getByPlaceholder("tablo_adi").fill(tableName);
  await page.getByPlaceholder("kolon_adi").first().fill("ad");
  await page.getByRole("button", { name: "Oluştur" }).click();

  await expect(page.getByText(`"${tableName}" tablosu oluşturuldu`)).toBeVisible();
  // Yeni tablo otomatik secilir; basligin gercekten bu tablo oldugunu dogrula. Baslik bir
  // <h2> degil, duzenlenebilir bir <input> (bkz. TabloDetail.tsx tablo-name-input).
  await expect(page.locator(".tablo-name-input")).toHaveValue(tableName);

  // 2) Ikinci bir kolon ekle — bu SADECE taslaga ekler, ne toast ne API istegi olur.
  await page.getByPlaceholder("kolon_adi").fill("yas");
  await page.getByRole("button", { name: "Kolon Ekle" }).click();

  // Kolon adi hucresi salt metin degil, duzenlenebilir bir <input value="yas"> (bkz.
  // TabloDetail.tsx) — input'un value'su textContent SAYILMAZ, o yuzden tr'ye hasText:"yas"
  // hicbir zaman eslesmez. Satiri, icindeki input'un value'suna gore buluyoruz.
  const yasRow = page.locator("tr").filter({ has: page.locator('input[value="yas"]') });
  await expect(yasRow).toBeVisible();
  await expect(yasRow.getByText("yeni")).toBeVisible(); // henuz kaydedilmemis kolon rozeti

  // 3) Kaydet — simdi gercekten API'ye gider, taslak backend'le birlesir.
  await page.getByRole("button", { name: "Kaydet" }).click();
  await expect(page.getByText("Değişiklikler kaydedildi")).toBeVisible();
  await expect(yasRow.getByText("yeni")).not.toBeVisible(); // artik kalici, "yeni" rozeti kalkar

  // 4) O kolonu sil olarak isaretle — kolon artik "yeni" degil, bu yuzden satirdan silinmez,
  // sadece "silinecek" olarak isaretlenir ve buton "Sil"den "Geri Al"a doner.
  await yasRow.getByRole("button", { name: "Sil" }).click();
  const undoButton = yasRow.getByRole("button", { name: "Geri Al" });
  await expect(undoButton).toBeVisible();

  // 5) Geri Al'a bas — satir hala orada, ve HENUZ hicbir DELETE istegi gitmedi (Kaydet'e
  // basilmadi), yani taslaktaki isaretin geri alinabilir oldugunu kanitlar.
  await undoButton.click();
  await expect(yasRow.getByRole("button", { name: "Sil" })).toBeVisible();

  // 6) Bu sefer gercekten sil: isaretle, Kaydet'e bas — DELETE artik gercekten gider.
  await yasRow.getByRole("button", { name: "Sil" }).click();
  await page.getByRole("button", { name: "Kaydet" }).click();
  await expect(page.getByText("Değişiklikler kaydedildi")).toBeVisible();
  await expect(yasRow).toHaveCount(0);

  // 7) Temizlik: butun test tablosunu gercekten sil.
  // ConfirmProvider window.confirm() yerine kendi modal'ini gosteriyor (bkz. notifications/ConfirmProvider.tsx)
  // — tetikleyici buton ve modal icindeki onay butonu ayni isme ("Sil") sahip oldugu icin
  // modal'i role="alertdialog" ile ayirt ediyoruz.
  await page.getByRole("button", { name: "Tabloyu Sil" }).click();
  const confirmDialog = page.getByRole("alertdialog");
  await confirmDialog.getByRole("button", { name: "Sil" }).click();
  await expect(page.getByText("Tablo silindi")).toBeVisible();

  await page.reload();
  await expect(page.getByRole("button", { name: tableName })).toHaveCount(0);
});
