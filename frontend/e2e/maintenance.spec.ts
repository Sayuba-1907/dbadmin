import { test, expect, Page } from "@playwright/test";

/** table-lifecycle.spec.ts'teki login yardimcisiyla ayni — her test taze bir tarayici context'inde baslar. */
async function login(page: Page) {
  await page.goto("/");
  await page.getByLabel("Kullanıcı adı").fill("admin");
  await page.getByLabel("Parola").fill("admin123");
  await page.getByRole("button", { name: "Giriş Yap" }).click();
  await expect(page.getByRole("button", { name: "+ Yeni Tablo" })).toBeVisible();
}

/**
 * requirement-maintenance-audit-backup.md Faz 6 Adim 6.4: "Bakım" sekmesinin ADMIN'e
 * gerçekten görünür/tıklanabilir olduğunu ve "Yedekle" butonunun uçtan uca (gerçek backend,
 * gerçek MinIO) çalıştığını doğrular.
 * <p>
 * Audit log tablosunun her zaman en az bir satır içerdiğinden emin olmak için (aksi halde
 * "Yedekle" 400 dönerdi, bu da test edilen davranışı belirsizleştirirdi) test önce kendi
 * tablosunu oluşturur — bu, backend'e gerçek bir TABLE_CREATED audit satırı yazar.
 */
test("Bakım sayfası: özet+sağlık görünür, audit log yedeklenince tablo boşalır", async ({ page }) => {
  test.setTimeout(90000);

  const tableName = `e2e_maintenance_${Date.now()}`;

  await login(page);

  // 1) En az bir audit satırı garanti et: kendi test tablomuzu oluştur.
  await page.getByRole("button", { name: "+ Yeni Tablo" }).click();
  await page.getByPlaceholder("tablo_adi").fill(tableName);
  await page.getByPlaceholder("kolon_adi").first().fill("ad");
  await page.getByRole("button", { name: "Oluştur" }).click();
  await expect(page.getByText(`"${tableName}" tablosu oluşturuldu`)).toBeVisible();

  // 2) Bakım sekmesine geç.
  await page.getByRole("button", { name: "Bakım" }).click();
  await expect(page.getByRole("heading", { name: "Bakım" })).toBeVisible();

  // 3) Özet kartları — etiketler CSS ile (text-transform:uppercase) büyük gösterilir, DOM
  // metni asıl (Şema/Tablo/Kolon/Kullanıcı) kalır, getByText onu arar. .first(): "Kullanıcı"
  // hem özet kartında hem audit log tablosunun sütun başlığında geçiyor (strict-mode'u atlatmak icin).
  await expect(page.getByText("Şema").first()).toBeVisible();
  await expect(page.getByText("Tablo").first()).toBeVisible();
  await expect(page.getByText("Kolon").first()).toBeVisible();
  await expect(page.getByText("Kullanıcı").first()).toBeVisible();

  // 4) Servis sağlığı — dev/docker-compose ortamında dördü de erişilebilir olmalı.
  await expect(page.getByText("Postgres").first()).toBeVisible();
  await expect(page.getByText("Redis").first()).toBeVisible();
  await expect(page.getByText("Tempo").first()).toBeVisible();
  await expect(page.getByText("Loki").first()).toBeVisible();

  // 5) Audit log tablosunda az önceki TABLE_CREATED satırı görünmeli.
  await expect(page.getByText("TABLE_CREATED").first()).toBeVisible();

  // 6) Yedekle — gerçek MinIO'ya yazar, tabloyu temizler.
  await page.getByRole("button", { name: "Yedekle" }).click();
  await expect(page.getByText(/audit log kaydı yedeklendi/)).toBeVisible();
  await expect(page.getByText("Kayıt yok")).toBeVisible();

  // 7) Temizlik: test tablosunu sil. Dashboard, gorunumler arasi gecerken secili tablonun
  // taslagini (draft) temizlemiyor (bkz. Dashboard.tsx handleChangeActiveView) — bu yuzden
  // "Şemalar"a donunce agactan yeniden tiklamaya gerek yok, TableDetail zaten aynı tabloyla
  // dogrudan render olur.
  await page.getByRole("button", { name: "Şemalar" }).click();
  await page.getByRole("button", { name: "Tabloyu Sil" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Sil" }).click();
  await expect(page.getByText("Tablo silindi")).toBeVisible();
});

/**
 * Req-3.4: ADMIN olmayan bir kullanıcı "Bakım" sekmesini hiç görmemeli. Var olan bir kullanıcının
 * şifresini varsaymak yerine (kırılgan), kendi geçici VIEWER kullanıcısını oluşturup test
 * sonunda temizler — table-lifecycle.spec.ts'teki "kendi test verini yarat, sonunda sil" deseni.
 */
test("VIEWER kullanıcı Bakım sekmesini göremez", async ({ page }) => {
  test.setTimeout(90000);

  const username = `e2e_viewer_${Date.now()}`;
  const password = "gecici_parola_123";

  await login(page);

  await page.getByRole("button", { name: "Kullanıcılar" }).click();
  await page.getByPlaceholder("kullanici_adi").fill(username);
  await page.getByPlaceholder("parola").fill(password);
  await page.getByRole("button", { name: "Kullanıcı Oluştur" }).click();
  await expect(page.getByText(`"${username}" kullanıcısı oluşturuldu`)).toBeVisible();

  await page.getByRole("button", { name: "Çıkış Yap" }).click();
  await page.getByLabel("Kullanıcı adı").fill(username);
  await page.getByLabel("Parola").fill(password);
  await page.getByRole("button", { name: "Giriş Yap" }).click();
  await expect(page.getByRole("button", { name: "Şemalar" })).toBeVisible();

  await expect(page.getByRole("button", { name: "Bakım" })).toHaveCount(0);

  // Temizlik: admin olarak geri gir, geçici kullanıcıyı sil.
  await page.getByRole("button", { name: "Çıkış Yap" }).click();
  await login(page);
  await page.getByRole("button", { name: "Kullanıcılar" }).click();
  const userRow = page.locator("tr").filter({ hasText: username });
  await userRow.getByRole("button", { name: "Sil" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Sil" }).click();
  await expect(page.getByText("Kullanıcı silindi")).toBeVisible();
});
