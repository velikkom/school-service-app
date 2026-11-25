// @ts-check
// Sprint 3 — Oturum ve e-posta akışları (izole ortam, Mailpit):
//  • erişim belirteci kontrollü biçimde doldurulur → gerçek domain isteği → BFF yenilemesi → istek tamamlanır,
//    kullanıcı oturumda kalır; çıkıştan sonra korumalı ekran açılmaz
//  • kök route `/`: oturumsuz → giriş, oturumlu → rolün ana sayfası
//  • kayıt talebi e-posta doğrulaması ve parola sıfırlama: geçerli bağlantı tamamlanır; yeniden kullanılan ve
//    süresi dolmuş bağlantı reddedilir
const crypto = require("node:crypto");
const { test, expect } = require("@playwright/test");
const { login, loadSeed } = require("./support/helpers");
const { adminApi, e2eEnv, mintExpiredAccessToken, waitForMail, extractLink, expireTokenInE2eDb } = require("./support/e2eEnv");
const { shortTag } = require("./support/ui");

test.describe.configure({ mode: "serial" });

const cookieHost = () => new URL(e2eEnv().baseUrl).hostname;

test("süresi dolan erişim belirteci domain isteğinde BFF ile yenilenir; çıkıştan sonra korumalı ekran açılmaz", async ({ page, context }) => {
  const seed = loadSeed();
  await login(page, seed.users.admin);
  await page.goto("/ogrenciler");
  await expect(page.getByRole("link", { name: seed.names.studentA })).toBeVisible();
  const me = await (await page.request.get("/api/auth/me")).json();

  // 1) Çerezde imzası geçerli ama süresi geçmiş JWT (sunucu tarafında süre dolumu).
  const expired = mintExpiredAccessToken({ userId: me.id, email: me.email, role: me.role });
  await context.addCookies([{ name: "ssp_at", value: expired, domain: cookieHost(), path: "/", httpOnly: true, sameSite: "Lax" }]);
  const refreshed = page.waitForResponse((r) => r.url().endsWith("/api/auth/refresh") && r.request().method() === "POST");
  const domainOk = page.waitForResponse((r) => r.url().includes("/api/students?") && r.url().includes("q=") && r.status() === 200);
  await page.getByLabel("Ara", { exact: true }).fill(seed.names.studentB);
  expect((await refreshed).status()).toBe(200);
  await domainOk;
  await expect(page.getByRole("link", { name: seed.names.studentB })).toBeVisible();
  await expect(page).toHaveURL(/\/ogrenciler/);
  const newAt = (await context.cookies()).find((c) => c.name === "ssp_at")?.value;
  expect(newAt && newAt !== expired).toBeTruthy();

  // 2) Tarayıcının süresi dolan çerezi silmesi (Max-Age) — çerez yokken de aynı yenileme.
  await context.clearCookies({ name: "ssp_at" });
  const refreshed2 = page.waitForResponse((r) => r.url().endsWith("/api/auth/refresh") && r.request().method() === "POST");
  await page.getByLabel("Ara", { exact: true }).fill(seed.names.studentA);
  expect((await refreshed2).status()).toBe(200);
  await expect(page.getByRole("link", { name: seed.names.studentA })).toBeVisible();
  await expect(page).toHaveURL(/\/ogrenciler/);

  // 3) Çıkış: korumalı ekran ve BFF oturumu kapanır.
  await page.goto("/profil");
  await page.locator("#icerik").getByRole("button", { name: "Çıkış yap" }).click();
  await expect(page).toHaveURL(/\/login/);
  await page.goto("/ogrenciler");
  await expect(page).toHaveURL(/\/login\?from=%2Fogrenciler/);
  expect((await page.request.get("/api/auth/me")).status()).toBe(401);
  expect((await page.request.get("/api/students")).status()).toBe(401);
  await page.goto("/");
  await expect(page).toHaveURL(/\/login$/);
});

test("kök route: oturumsuz giriş sayfasına, oturumlu kullanıcı rolünün ana sayfasına gider", async ({ browser }) => {
  const seed = loadSeed();
  const anon = await browser.newPage();
  await anon.goto("/");
  await expect(anon).toHaveURL(/\/login$/);
  await anon.close();

  const homes = [
    ["admin", /\/dashboard$/],
    ["driver", /\/seferler$/],
    ["hostess", /\/seferler$/],
    ["parent", /\/servisim$/],
    ["student", /\/servisim$/],
  ];
  for (const [role, home] of homes) {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await login(page, seed.users[role]);
    await page.goto("/");
    await expect(page, `${role} → ana sayfa`).toHaveURL(home);
    // Erişim çerezi dolmuş (silinmiş) ama oturum sürüyorsa da ana sayfaya ulaşır.
    await ctx.clearCookies({ name: "ssp_at" });
    await page.goto("/");
    await expect(page, `${role} (erişim çerezi dolmuş) → ana sayfa`).toHaveURL(home);
    await ctx.close();
  }
});

test("kayıt talebi: Mailpit'teki geçerli doğrulama bağlantısı tamamlanır; tekrar ve süresi dolmuş bağlantı reddedilir", async ({ page }) => {
  const tag = shortTag();
  const fill = async (email) => {
    await page.goto("/kayit-talebi");
    await page.getByRole("textbox", { name: "Ad", exact: true }).fill("Sprint");
    await page.getByLabel("Soyad").fill(`Üç ${tag}`);
    await page.getByLabel("E-posta").fill(email);
    await page.locator("#pw-password").fill("S3e2e-Kayit-1234");
    await page.locator("#pw-passwordConfirm").fill("S3e2e-Kayit-1234");
    await page.getByRole("button", { name: "Talep gönder" }).click();
    await expect(page.getByText("Talebiniz alındı")).toBeVisible();
  };

  const email = `s3e2e.reg.${tag.toLowerCase()}@example.test`;
  const since = Date.now() - 1000;
  await fill(email);
  const mail = await waitForMail(email, { after: since });
  const link = extractLink(mail, "/e-posta-dogrula");
  expect(new URL(link).origin).toBe(new URL(e2eEnv().baseUrl).origin);
  await page.goto(link);
  await expect(page.getByText("E-posta adresiniz doğrulandı")).toBeVisible();

  // ADMIN kuyruğunda doğrulanmış talep görünür.
  const api = await adminApi();
  const reqs = await api("GET", "/api/registration-requests?size=100");
  const mine = (reqs.content ?? reqs).find((r) => r.email === email);
  expect(mine, "talep ADMIN listesinde").toBeTruthy();
  expect(mine.status).toBe("EMAIL_VERIFIED");
  expect(mine.emailVerifiedAt).toBeTruthy();

  // Aynı bağlantı ikinci kez: reddedilir.
  await page.goto(link);
  await expect(page.getByText("Doğrulama bağlantısı geçersiz")).toBeVisible();

  // Süresi dolmuş bağlantı: reddedilir.
  const email2 = `s3e2e.reg2.${tag.toLowerCase()}@example.test`;
  const since2 = Date.now() - 1000;
  await fill(email2);
  const link2 = extractLink(await waitForMail(email2, { after: since2 }), "/e-posta-dogrula");
  expireTokenInE2eDb("registration_verification_token", new URL(link2).searchParams.get("token") ?? "");
  await page.goto(link2);
  await expect(page.getByText("Doğrulama bağlantısı geçersiz")).toBeVisible();
});

test("parola sıfırlama: geçerli bağlantıyla yeni parola, yeni parolayla giriş; tekrar ve süresi dolmuş bağlantı reddedilir", async ({ page }) => {
  const api = await adminApi();
  const tag = shortTag();
  const oldPassword = `S3e2e-${crypto.randomBytes(5).toString("hex")}`;
  const email = `s3e2e.reset.${tag.toLowerCase()}@example.test`;
  const { user } = await api("POST", "/api/users", { fullName: `S3 Sıfırlama ${tag}`, email, role: "HOSTESS", password: oldPassword });
  await api("POST", `/api/users/${user.id}/role`, { role: "HOSTESS" });

  const requestReset = async () => {
    const since = Date.now() - 1000;
    await page.goto("/sifremi-unuttum");
    await page.getByLabel("E-posta").fill(email);
    await page.getByRole("button", { name: "Sıfırlama bağlantısı gönder" }).click();
    await expect(page.getByText("E-postanızı kontrol edin")).toBeVisible();
    const link = extractLink(await waitForMail(email, { after: since }), "/sifre-sifirla");
    expect(new URL(link).origin).toBe(new URL(e2eEnv().baseUrl).origin);
    return link;
  };
  const submitNew = async (link, password) => {
    await page.goto(link);
    await page.locator("#pw-newPassword").fill(password);
    await page.locator("#pw-confirm").fill(password);
    await page.getByRole("button", { name: "Şifreyi güncelle" }).click();
  };

  const link = await requestReset();
  const newPassword = `S3yeni-${crypto.randomBytes(5).toString("hex")}`;
  await submitNew(link, newPassword);
  await expect(page.getByText("Şifreniz güncellendi")).toBeVisible();

  await login(page, { email, password: newPassword });
  await expect(page).toHaveURL(/\/seferler/);
  await page.goto("/profil");
  await page.locator("#icerik").getByRole("button", { name: "Çıkış yap" }).click();
  await expect(page).toHaveURL(/\/login/);

  // Eski parola artık geçmez.
  await page.getByLabel("E-posta").fill(email);
  await page.locator("#login-password").fill(oldPassword);
  await page.getByRole("button", { name: "Giriş yap" }).click();
  await expect(page.getByText("E-posta veya şifre hatalı.")).toBeVisible();

  // Kullanılmış bağlantı tekrar: reddedilir.
  await submitNew(link, `S3tekrar-${crypto.randomBytes(4).toString("hex")}`);
  await expect(page.getByText("Bağlantının süresi dolmuş veya daha önce kullanılmış")).toBeVisible();

  // Süresi dolmuş bağlantı: reddedilir, parola değişmez.
  const link2 = await requestReset();
  expireTokenInE2eDb("password_reset_token", new URL(link2).searchParams.get("token") ?? "");
  await submitNew(link2, `S3dolmus-${crypto.randomBytes(4).toString("hex")}`);
  await expect(page.getByText("Bağlantının süresi dolmuş veya daha önce kullanılmış")).toBeVisible();
  await login(page, { email, password: newPassword });
  await expect(page).toHaveURL(/\/seferler/);
});
