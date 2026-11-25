// @ts-check
// Sprint 3 — Yalnız izole ortamda, test hesaplarıyla: kurum ayarlarını kaydetme, parola değiştirme, hedefli
// toplu bildirim (in-app + e-posta → yalnız E2E Mailpit).
const crypto = require("node:crypto");
const { test, expect } = require("@playwright/test");
const { login, loadSeed } = require("./support/helpers");
const { adminApi, waitForMail, allMailRecipients } = require("./support/e2eEnv");
const { expectToast, acceptConfirm, shortTag } = require("./support/ui");

test.describe.configure({ mode: "serial" });

async function createUser(api, role, tag, fullName) {
  const password = `S3e2e-${crypto.randomBytes(6).toString("hex")}`;
  const email = `s3e2e.${role.toLowerCase()}.${tag.toLowerCase()}@example.test`;
  const { user } = await api("POST", "/api/users", { fullName, email, role, password, phone: "05550001122" });
  await api("POST", `/api/users/${user.id}/role`, { role });
  return { id: user.id, email, password, fullName };
}

/** Bağlı öğrencisi olan veli (öz-kayıt kapısına takılmaz). */
async function createParent(api, tag, studentId) {
  const parentUser = await createUser(api, "PARENT", tag, `S3 Bildirim Velisi ${tag}`);
  const parent = (await api("GET", "/api/parents?size=100&sort=id,desc")).content.find((p) => p.userId === parentUser.id);
  await api("POST", "/api/parent-student-links", { parentId: parent.id, studentId, relationType: "MOTHER" });
  return parentUser;
}

test("kurum ayarları kaydedilir ve yenilemeden sonra okunur", async ({ page }) => {
  const seed = loadSeed();
  const tag = shortTag();
  await login(page, seed.users.admin);
  await page.goto("/ayarlar");
  await page.getByLabel("Kurum adı").fill(`S3 E2E Servis ${tag}`);
  await page.getByLabel("Telefon").fill("02120000000");
  await page.getByLabel("Varsayılan aylık ücret (TL)").fill("1750");
  await page.getByLabel("Belge notu").fill(`E2E belge notu ${tag}`);
  await page.getByRole("button", { name: "Kaydet" }).click();
  await expectToast(page, "Ayarlar kaydedildi");
  await page.reload();
  await expect(page.getByLabel("Kurum adı")).toHaveValue(`S3 E2E Servis ${tag}`);
  await expect(page.getByLabel("Belge notu")).toHaveValue(`E2E belge notu ${tag}`);
  await expect(page.getByLabel("Varsayılan aylık ücret (TL)")).toHaveValue(/1750/);
});

test("parola değiştirme: yeni parolayla giriş, eski parola reddedilir", async ({ page }) => {
  const api = await adminApi();
  const user = await createUser(api, "HOSTESS", `PW${shortTag()}`, "S3 Parola Hostesi");
  const newPassword = `S3yeni-${crypto.randomBytes(5).toString("hex")}`;
  await login(page, user);
  await page.goto("/profil");
  await page.locator("#pw-currentPassword").fill(user.password);
  await page.locator("#pw-newPassword").fill(newPassword);
  await page.locator("#pw-confirm").fill(newPassword);
  await page.getByRole("button", { name: "Şifreyi değiştir" }).click();
  await expectToast(page, "Şifreniz değiştirildi");
  await expect(page).toHaveURL(/\/login/, { timeout: 20_000 });

  await page.getByLabel("E-posta").fill(user.email);
  await page.locator("#login-password").fill(user.password);
  await page.getByRole("button", { name: "Giriş yap" }).click();
  await expect(page.getByText("E-posta veya şifre hatalı.")).toBeVisible();

  await login(page, { ...user, password: newPassword });
  await expect(page).toHaveURL(/\/seferler/);
});

test("toplu bildirim hedeflenen test alıcısına ulaşır (in-app + Mailpit), posta dışarı çıkmaz", async ({ browser }) => {
  const seed = loadSeed();
  const api = await adminApi();
  const tag = shortTag();
  const target = await createParent(api, `BC${tag}`, seed.ids.studentB);
  const title = `S3 Duyuru ${tag}`;
  const body = `Yarın servis saatleri 10 dakika erken. (${tag})`;
  const sentAt = Date.now() - 1000;

  const ctx = await browser.newContext();
  const admin = await ctx.newPage();
  await login(admin, seed.users.admin);
  await admin.goto("/bildirimler/toplu");
  await admin.getByText("Seçili veliler", { exact: true }).click();
  await admin.locator(".p-multiselect:has(#bc-parents)").click();
  const panel = admin.locator(".p-multiselect-panel");
  await panel.locator("input.p-multiselect-filter").fill(target.fullName);
  await panel.getByRole("option", { name: new RegExp(target.fullName) }).click();
  await admin.keyboard.press("Escape");
  await admin.getByLabel("Başlık").fill(title);
  await admin.getByLabel("Mesaj").fill(body);
  await admin.getByRole("button", { name: "Gönder", exact: true }).click();
  await acceptConfirm(admin, "Gönder");
  await expect(admin.locator("#icerik").getByText("Duyuru gönderildi")).toBeVisible();
  await expect(admin.locator("#icerik").getByText(`"${title}" 1 alıcıya iletildi.`)).toBeVisible();

  const mail = await waitForMail(target.email, { subject: title, after: sentAt });
  expect(mail.to).toEqual([target.email]);
  expect(mail.text).toContain(body);

  // Alıcı uygulama içinde de görür.
  const pctx = await browser.newContext();
  const parent = await pctx.newPage();
  await login(parent, target);
  await parent.goto("/bildirimler");
  await expect(parent.getByText(title).first()).toBeVisible();

  // Mailpit'teki her alıcı sentetik test alanında (dış adres yok).
  const recipients = await allMailRecipients();
  expect(recipients.length).toBeGreaterThan(0);
  expect(recipients.filter((r) => !r.endsWith("@example.test"))).toEqual([]);
  await ctx.close();
  await pctx.close();
});
