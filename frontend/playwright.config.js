// @ts-check
const { defineConfig, devices } = require("@playwright/test");

/**
 * E2E (Sprint 3): yalnız izole ortamda — scripts\e2e.ps1 tek kullanımlık PostgreSQL/Mailpit/backend'i kurar,
 * bu yapılandırma Next.js'i ayrı portta (varsayılan 3100) ve ayrı derleme klasöründe (.next-e2e) başlatır.
 * Ortam değişkenleri yoksa global setup koşuyu durdurur (bkz. e2e/support/e2eEnv.js).
 */
const port = Number(process.env.E2E_FRONTEND_PORT || 3100);
const baseURL = process.env.E2E_BASE_URL || `http://localhost:${port}`;

module.exports = defineConfig({
  testDir: "./e2e",
  testMatch: /.*\.spec\.js$/,
  globalSetup: require.resolve("./e2e/global-setup.js"),
  timeout: 180_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "off",
    screenshot: "only-on-failure",
    locale: "tr-TR",
    timezoneId: "Europe/Istanbul",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: `npx next dev -p ${port}`,
    url: `${baseURL}/login`,
    // Yanlış (ör. günlük) sunucuya bağlanmamak için mevcut sunucu yeniden kullanılmaz.
    reuseExistingServer: false,
    timeout: 240 * 1000,
    stdout: "ignore",
    stderr: "pipe",
  },
});
