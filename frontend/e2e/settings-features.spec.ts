import { test, expect, Page } from "@playwright/test";

/** Diger e2e testleriyle ayni login yardimcisi (bkz. table-lifecycle.spec.ts / maintenance.spec.ts). */
async function login(page: Page) {
  await page.goto("/");
  await page.getByLabel("Kullanıcı adı").fill("admin");
  await page.getByLabel("Parola").fill("admin123");
  await page.getByRole("button", { name: "Giriş Yap" }).click();
  await expect(page.getByRole("button", { name: "+ Yeni Tablo" })).toBeVisible();
}

/** Sol-alt hesap kartina tiklayip popup'i acar — WorkspaceNav.tsx'teki tek "hesap" butonu. */
async function openAccountMenu(page: Page) {
  await page.locator(".workspace-nav-account").getByRole("button", { name: "admin" }).click();
}

/** Hesap popup'undaki "Ayarlar" flyout tetikleyicisini acar (Oturumlar/Yönetim/Faydalı Linkler). */
async function openSettingsFlyout(page: Page) {
  await page.getByRole("menuitem", { name: "Ayarlar" }).click();
}

/**
 * Bu oturumda eklenen ucu ozellik: Profil sayfasina e-posta alani (bkz. AuthController#updateProfile,
 * ProfilePanel.tsx). Gecerli bir adresle kaydedip kalici oldugunu (reload sonrasi /me'den geri
 * geldigini), gecersiz bir adresle de backend'in 400 dondurup cevrilmis hata mesaji gosterdigini
 * dogrular.
 */
test("Profil: e-posta güncellenir, kalıcı kalır; geçersiz adres reddedilir", async ({ page }) => {
  test.setTimeout(60000);
  const email = `e2e_${Date.now()}@example.com`;

  await login(page);
  await openAccountMenu(page);
  await page.getByRole("menuitem", { name: "Profil" }).click();
  await expect(page.getByRole("heading", { name: "Profil", exact: true })).toBeVisible();

  // Gecersiz adres: "a@b" native <input type="email"> dogrulamasini gecer (whatwg kurali nokta
  // istemiyor) ama backend'in EmailValidator regex'i (@ sonrasi nokta sart) reddeder — boylece
  // istek gercekten backend'e gidip VALIDATION_INVALID_EMAIL'e dusuyor, cevrilmis mesaj gosterilir.
  await page.getByLabel("E-posta").fill("a@b");
  await page.getByRole("button", { name: "Kaydet" }).click();
  await expect(page.getByText("Geçerli bir e-posta adresi girin")).toBeVisible();

  // Gecerli adres: kaydedilir ve toast gosterilir.
  await page.getByLabel("E-posta").fill(email);
  await page.getByRole("button", { name: "Kaydet" }).click();
  await expect(page.getByText("Profil güncellendi")).toBeVisible();

  // Kalicilik: sayfa yenilenince /api/auth/me'den geri gelmeli.
  await page.reload();
  await openAccountMenu(page);
  await page.getByRole("menuitem", { name: "Profil" }).click();
  await expect(page.getByLabel("E-posta")).toHaveValue(email);
});

/**
 * Bu oturumda genisletilen "Faydalı Linkler": MinIO/RedisInsight/RabbitMQ eklendi, iki gruba
 * ayrildi (bkz. UsefulLinksPanel.tsx). Sadece ADMIN'e acik oldugu icin ayri bir VIEWER testi
 * yerine (maintenance.spec.ts'teki "Bakım sekmesini göremez" testiyle ayni fikir zaten var)
 * burada sadece ADMIN gorunumunun tam icerigini dogruluyoruz.
 */
test("Faydalı Linkler: gruplu ve tüm servisler linkli görünür", async ({ page }) => {
  test.setTimeout(60000);

  await login(page);
  await openAccountMenu(page);
  await openSettingsFlyout(page);
  await page.getByRole("menuitem", { name: "Faydalı Linkler" }).click();
  await expect(page.getByRole("heading", { name: "Faydalı Linkler" })).toBeVisible();

  await expect(page.getByText("İzleme & API")).toBeVisible();
  await expect(page.getByText("Altyapı Servisleri")).toBeVisible();

  const links: [string, string][] = [
    ["Swagger UI", ":8081"],
    ["Grafana", ":3001"],
    ["Prometheus", ":9090"],
    ["MinIO Console", ":9001"],
    ["RedisInsight", ":5540"],
    ["RabbitMQ", ":15672"],
  ];
  for (const [name, port] of links) {
    const link = page.getByRole("link", { name: new RegExp(name) });
    await expect(link).toBeVisible();
    await expect(await link.getAttribute("href")).toContain(port);
    await expect(await link.getAttribute("target")).toBe("_blank");
  }
});

/**
 * Bu oturumda eklenen "demo'da iyi duran" ozellik (bkz. backend/notlar madde 10): Audit Log'un
 * Timeline gorunumu. Table<->Timeline gecisinin calistigini ve Timeline'da en az bir olay
 * kartinin (renkli marker + rozet + kullanici/hedef + detay) gorundugunu dogrular — once kendi
 * tablomuzu olusturup en az bir TABLE_CREATED satiri garanti ediyoruz (maintenance.spec.ts'teki
 * "audit log her zaman en az bir satir icersin" deseniyle ayni gerekce).
 */
test("Yönetim > Audit Log: Timeline görünümüne geçilebilir", async ({ page }) => {
  test.setTimeout(60000);
  const tableName = `e2e_timeline_${Date.now()}`;

  await login(page);

  await page.getByRole("button", { name: "+ Yeni Tablo" }).click();
  await page.getByPlaceholder("tablo_adi").fill(tableName);
  await page.getByPlaceholder("kolon_adi").first().fill("ad");
  await page.getByRole("button", { name: "Oluştur" }).click();
  await expect(page.getByText(`"${tableName}" tablosu oluşturuldu`)).toBeVisible();

  await openAccountMenu(page);
  await openSettingsFlyout(page);
  await page.getByRole("menuitem", { name: "Yönetim" }).click();
  await page.getByRole("tab", { name: "Audit Log" }).click();

  // Varsayilan gorunum Tablo — en azindan az once yarattigimiz satir gorunmeli.
  await expect(page.getByText("TABLE_CREATED").first()).toBeVisible();

  await page.getByRole("tab", { name: "Zaman Çizelgesi" }).click();
  await expect(page.locator(".audit-timeline-card").first()).toBeVisible();
  await expect(page.locator(".audit-timeline-dot").first()).toBeVisible();
  await expect(page.getByText("TABLE_CREATED").first()).toBeVisible();

  await page.getByRole("tab", { name: "Tablo" }).click();
  await expect(page.locator(".audit-log-table")).toBeVisible();

  // Temizlik: test tablosunu sil.
  await page.getByRole("button", { name: "Şemalar" }).click();
  await page.getByRole("button", { name: "Tabloyu Sil" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Sil" }).click();
  await expect(page.getByText("Tablo silindi")).toBeVisible();
  // Gercek DELETE istegi hemen gitmez: Dashboard.tsx#handleDeleteTablo, "Geri Al" penceresi
  // (NOTIFICATION_DURATION_MS=5s) boyunca sadece iyimser/optimistic olarak UI'dan kaldirir,
  // asil istegi bir setTimeout ile erteler. Test burada hemen bitip sayfayi kapatirsa o
  // zamanlayici hic ateslenmez ve tablo backend'de KALIR (bu suitedeki diger testlerde de
  // gozlemlenen bir durum) — toast'in kendiliginden kapanmasini beklemek gecikmenin gectigini,
  // yani gercek silmenin gittigini garanti eder.
  await expect(page.getByText("Tablo silindi")).toBeHidden({ timeout: 8000 });
});
