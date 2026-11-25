// @ts-check
const fs = require("node:fs");
const path = require("node:path");

/** Global setup'ın ürettiği izole test verisi. */
function loadSeed() {
  return JSON.parse(fs.readFileSync(path.join(__dirname, "..", ".seed.json"), "utf8"));
}

/** Gerçek giriş formu üzerinden oturum açar (BFF çerezleri bu bağlamda kalır). */
async function login(page, user) {
  await page.goto("/login");
  await page.getByLabel("E-posta").fill(user.email);
  await page.locator("#login-password").fill(user.password);
  await page.getByRole("button", { name: "Giriş yap" }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 30_000 });
}

/** Aynı oturumla BFF üzerinden API çağrısı (CSRF başlığı çerezden). */
async function bff(page, method, url, data) {
  const cookies = await page.context().cookies();
  const csrf = cookies.find((c) => c.name === "ssp_csrf")?.value;
  const res = await page.request.fetch(`/api${url}`, {
    method,
    data,
    headers: csrf ? { "X-CSRF-Token": csrf } : {},
  });
  const text = await res.text();
  return { status: res.status(), body: text ? JSON.parse(text) : null };
}

module.exports = { loadSeed, login, bff };
