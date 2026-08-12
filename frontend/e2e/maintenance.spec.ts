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
 * "Ayarlar Sayfası" requirement notunun 3. revizyonundan (bkz. WorkspaceNav.tsx'in kendi
 * javadoc'u) beri "Bakım" ust seviye bir sekme DEGIL — sol-alt hesap kartina tiklayinca acilan
 * popup'ta "Ayarlar" flyout'unun "Yönetim" secenegi. settings-features.spec.ts'teki ayni
 * yardimcilarla birebir ayni.
 */
async function openAccountMenu(page: Page) {
  // Butonun metni giris yapan kullanicinin username'i (admin ya da gecici VIEWER olabilir) —
  // isimle degil, kapsayan tek container'a gore seciyoruz (icinde baska buton yok).
  await page.locator(".workspace-nav-account > button").click();
}

async function openMaintenancePage(page: Page) {
  await openAccountMenu(page);
  await page.getByRole("menuitem", { name: "Ayarlar" }).click();
  await page.getByRole("menuitem", { name: "Yönetim" }).click();
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

  // 2) Yönetim sayfasına geç (hesap popup > Ayarlar > Yönetim — bkz. openMaintenancePage).
  await openMaintenancePage(page);
  await expect(page.getByRole("heading", { name: "Bakım" })).toBeVisible();

  // 3) Özet kartları — varsayılan aktif tab zaten "Entities" (bkz. MaintenancePanel
  // MAINTENANCE_TABS ilk elemanı). Etiketler CSS ile (text-transform:uppercase) büyük
  // gösterilir, DOM metni asıl (Şema/Tablo/Kolon/Kullanıcı) kalır, getByText onu arar.
  // .first(): "Kullanıcı" hem özet kartında hem audit log tablosunun sütun başlığında geçiyor
  // (strict-mode'u atlatmak icin).
  await expect(page.getByText("Şema").first()).toBeVisible();
  await expect(page.getByText("Tablo").first()).toBeVisible();
  await expect(page.getByText("Kolon").first()).toBeVisible();
  await expect(page.getByText("Kullanıcı").first()).toBeVisible();

  // 4) Servis sağlığı kendi sekmesinde (bkz. MaintenancePanel HEALTH_SERVICES) — Tempo/Loki
  // buradan bilerek çıkarıldı (backend/notlar madde 10: "Servis Durumlarında -> Minio, Backend
  // -> Tempo ve Loki kaldır buradan"), o ikisi artık burada gösterilmiyor.
  await page.getByRole("tab", { name: "Servis Durumları" }).click();
  await expect(page.getByText("Postgres").first()).toBeVisible();
  await expect(page.getByText("Redis").first()).toBeVisible();
  await expect(page.getByText("MinIO").first()).toBeVisible();
  await expect(page.getByText("Backend").first()).toBeVisible();

  // 5) Audit log tablosunda az önceki TABLE_CREATED satırı görünmeli.
  await page.getByRole("tab", { name: "Audit Log" }).click();
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
  // Gercek DELETE istegi hemen gitmez: Dashboard.tsx#handleDeleteTablo, "Geri Al" penceresi
  // (NOTIFICATION_DURATION_MS=5s) boyunca sadece iyimser/optimistic olarak UI'dan kaldirir,
  // asil istegi bir setTimeout ile erteler — test hemen bitip sayfayi kapatirsa o zamanlayici
  // hic ateslenmez ve tablo backend'de KALIR. Toast'in kendiliginden kapanmasini beklemek
  // gecikmenin gectigini garanti eder.
  await expect(page.getByText("Tablo silindi")).toBeHidden({ timeout: 8000 });
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

  // "Yönetim" (ve "Faydalı Linkler") flyout'ta isAdmin degilse hic render edilmiyor (bkz.
  // WorkspaceNav.tsx) — VIEWER sadece "Oturumlar"i gormeli.
  await openAccountMenu(page);
  await page.getByRole("menuitem", { name: "Ayarlar" }).click();
  await expect(page.getByRole("menuitem", { name: "Oturumlar" })).toBeVisible();
  await expect(page.getByRole("menuitem", { name: "Yönetim" })).toHaveCount(0);
  await expect(page.getByRole("menuitem", { name: "Faydalı Linkler" })).toHaveCount(0);

  // Temizlik: admin olarak geri gir, geçici kullanıcıyı sil.
  await page.getByRole("button", { name: "Çıkış Yap" }).click();
  await login(page);
  await page.getByRole("button", { name: "Kullanıcılar" }).click();
  const userRow = page.locator("tr").filter({ hasText: username });
  await userRow.getByRole("button", { name: "Sil" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Sil" }).click();
  await expect(page.getByText("Kullanıcı silindi")).toBeVisible();
  // Dashboard.tsx#handleDeleteKullanici de ayni gecikmeli-silme desenini kullanir (bkz.
  // handleDeleteTablo'daki ayni gerekce) — toast'in kendiliginden kapanmasini beklemek gercek
  // DELETE'in gittigini garanti eder, aksi halde bu gecici kullanici backend'de kalirdi.
  await expect(page.getByText("Kullanıcı silindi")).toBeHidden({ timeout: 8000 });
});
