// @ts-check
// Sprint 3 — STUDENT hesabını öğrenci kaydına bağlama (arayüzden; user_id'ye elle SQL yazılmaz).
// ADMIN yeni hesabı mevcut hesap oluşturma akışıyla hazırlayıp bağlar → STUDENT giriş yapar ve kendi profil,
// servis ve yoklama verisini görür → ADMIN bağı kaldırır → açık oturum ve arayüz önbelleği eski erişimi
// sürdüremez. Ayrıca "Mevcut hesap" ile bağlama. Gerçek backend + BFF, izole ssp_e2e veritabanı.
const { test, expect } = require("@playwright/test");
const { login, loadSeed } = require("./support/helpers");
const { mintExpiredAccessToken, e2eEnv } = require("./support/e2eEnv");
const { buildServiceFixture, createStudentAccount } = require("./support/fixtures");
const { expectToast, submitDialog, closeDialog, acceptConfirm } = require("./support/ui");

test.describe.configure({ mode: "serial" });

function accountCard(page) {
  return page.locator("section", { has: page.getByRole("heading", { name: "Öğrenci hesabı", exact: true }) });
}

test("yeni STUDENT hesabı arayüzden bağlanır, öğrenci kendi verisini görür, bağ kaldırılınca erişim biter", async ({ browser }) => {
  const seed = loadSeed();
  const fx = await buildServiceFixture("SA");
  const email = `s3e2e.student.ui.${fx.tag.toLowerCase()}@example.test`;

  const adminCtx = await browser.newContext();
  const admin = await adminCtx.newPage();
  await login(admin, seed.users.admin);
  await admin.goto(`/ogrenciler/${fx.student.id}`);
  const card = accountCard(admin);
  await expect(card.getByText("Bağlı hesap yok")).toBeVisible();

  // Mevcut hesap oluşturma akışı (POST /users, role=STUDENT) + bağlama (PUT /students/{id}/account).
  await card.getByRole("button", { name: "Hesap bağla" }).click();
  const dlg = admin.getByRole("dialog");
  await dlg.getByText("Yeni hesap oluştur", { exact: true }).click();
  await expect(dlg.getByLabel("Ad soyad")).toHaveValue(fx.student.fullName);
  await dlg.getByLabel("E-posta").fill(email);
  await submitDialog(admin, "Bağla");
  await expectToast(admin, "Öğrenci hesabı bağlandı");
  const done = admin.getByRole("dialog");
  await expect(done.getByText("Geçici şifre (bir kez gösterilir)")).toBeVisible();
  const tempPassword = (await done.locator("b.mono").textContent())?.trim();
  expect(tempPassword).toBeTruthy();
  await closeDialog(admin);
  await expect(card.getByText(email)).toBeVisible();
  await expect(card.getByText("Bağlı · aktif")).toBeVisible();

  // Yenilemeden sonra bağ sunucudan tekrar okunur.
  await admin.reload();
  await expect(accountCard(admin).getByText(email)).toBeVisible();

  // STUDENT: kendi profil, servis ve yoklama verisi.
  const studentCtx = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const student = await studentCtx.newPage();
  await login(student, { email, password: tempPassword });
  await expect(student).toHaveURL(/\/servisim/);
  await expect(student.getByText(fx.student.fullName).filter({ visible: true }).first()).toBeVisible();
  await expect(student.getByText(fx.route.name).filter({ visible: true }).first()).toBeVisible();
  await student.goto("/yoklama");
  await expect(student.getByText("Bindi").filter({ visible: true }).first()).toBeVisible();
  await student.goto("/atamalar");
  await expect(student.getByText(fx.route.name).filter({ visible: true }).first()).toBeVisible();
  await student.goto("/profil");
  await expect(student.getByText("Bilgilerim").filter({ visible: true }).first()).toBeVisible();
  await expect(student.getByText(fx.student.fullName).filter({ visible: true }).first()).toBeVisible();

  // STUDENT başka bir öğrencinin kaydını göremez (BFF üzerinden doğrudan istek).
  const foreign = await student.request.get(`/api/students/${seed.ids.studentB}`);
  expect(foreign.status()).toBe(404);

  // ADMIN bağı kaldırır (onaylı).
  await accountCard(admin).getByRole("button", { name: "Bağı kaldır" }).click();
  await acceptConfirm(admin, "Bağı kaldır");
  await expectToast(admin, "Hesap bağı kaldırıldı");
  await admin.reload();
  await expect(accountCard(admin).getByText("Bağlı hesap yok")).toBeVisible();

  // Açık öğrenci oturumu: istemci içi gezinme (önbellek) ve tam yenileme eski veriyi göstermez.
  await student.goto("/servisim");
  await expect(student.getByText("Öğrenci kaydın bağlı değil")).toBeVisible();
  await expect(student.getByText(fx.route.name)).toHaveCount(0);
  await student.getByRole("link", { name: /Yoklama/ }).first().click();
  await expect(student.getByText("Öğrenci kaydın bağlı değil")).toBeVisible();
  await expect(student.getByText("Bindi")).toHaveCount(0);

  // Erişim belirteci dolduğunda eski oturum yenilenemez (bağ kaldırılırken refresh token'lar iptal edildi).
  const me = await (await student.request.get("/api/auth/me")).json();
  const host = new URL(e2eEnv().baseUrl).hostname;
  await studentCtx.addCookies([{ name: "ssp_at", value: mintExpiredAccessToken({ userId: me.id, email, role: "STUDENT" }), domain: host, path: "/", httpOnly: true, sameSite: "Lax" }]);
  await student.goto("/servisim");
  await expect(student).toHaveURL(/\/login/);

  await adminCtx.close();
  await studentCtx.close();
});

test("mevcut STUDENT hesabı listeden seçilerek bağlanır; liste yalnız bağlanabilir hesapları gösterir", async ({ browser }) => {
  const seed = loadSeed();
  const fx = await buildServiceFixture("SB");
  const account = await createStudentAccount(fx.api, fx.tag);

  const ctx = await browser.newContext();
  const admin = await ctx.newPage();
  await login(admin, seed.users.admin);
  await admin.goto(`/ogrenciler/${fx.student.id}`);
  await accountCard(admin).getByRole("button", { name: "Hesap bağla" }).click();
  // Seed öğrencisine zaten bağlı olan hesap listede yer almaz.
  await admin.locator(".p-dropdown:has(#student-account-user)").click();
  const panel = admin.locator(".p-dropdown-panel");
  await expect(panel.getByRole("option", { name: account.email })).toBeVisible();
  await expect(panel.getByRole("option", { name: seed.users.student.email })).toHaveCount(0);
  // (Escape açık dialogu da kapattığı için seçim aynı panelden yapılır.)
  await panel.getByRole("option", { name: account.email }).click();
  await expect(panel).toHaveCount(0);
  await submitDialog(admin, "Bağla");
  await expectToast(admin, "Öğrenci hesabı bağlandı");
  await admin.reload();
  await expect(accountCard(admin).getByText(account.email)).toBeVisible();

  const sctx = await browser.newContext();
  const student = await sctx.newPage();
  await login(student, account);
  await expect(student.getByText(fx.student.fullName).filter({ visible: true }).first()).toBeVisible();
  await ctx.close();
  await sctx.close();
});
