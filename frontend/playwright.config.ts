import { defineConfig, devices } from "@playwright/test";

/**
 * e2e testleri Jest/RTL testlerinden farkli bir katman: burada hicbir sey mock'lanmiyor —
 * gercek bir Chromium acilir, gercek `npm start` dev server'ina, o da gercek backend'e
 * (localhost:8080, ki o da gercek Postgres'e) istek atar. Amac: "gercekten kullanici gibi
 * tiklayinca calisiyor mu" sorusunu cevaplamak — Jest'teki fetch mock'lari asla yakalayamayacagi
 * turden hatalari (native `confirm()` diyaloglari, gercek CSS/layout, gercek network gecikmesi) yakalar.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60000,
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  // reuseExistingServer: dev server zaten calisiyorsa (npm start elle baslatilmissa) onu
  // kullanir, yeni bir tane baslatmaya calismaz — CI'da ise kendisi baslatir.
  webServer: {
    command: "npm start",
    url: "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 120000,
  },
});
