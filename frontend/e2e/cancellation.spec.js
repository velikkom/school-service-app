// @ts-check
// Saha iptal TALEBİ (sefer durumunu değiştirmez, ADMIN karar verir) ile ADMIN'in doğrudan, gerekçeli
// iptali ayrı kontrollerdir. Gerçek backend + BFF.
const { test, expect } = require("@playwright/test");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
const fs = require("node:fs");
const { login } = require("./support/helpers");

test("saha iptal talebi ve ADMIN doğrudan iptali ayrı işler", async ({ browser }) => {
  // Bu test seferleri iptal ettiği için diğer akışlardan ayrı, kendi izole verisini üretir.
  const seedFile = path.join(__dirname, ".seed-cancellation.json");
  execFileSync(process.execPath, [path.join(__dirname, "support", "seed.mjs")], { stdio: "inherit", env: { ...process.env, SEED_OUT: seedFile } });
  const seed = JSON.parse(fs.readFileSync(seedFile, "utf8"));
  const driverCtx = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const adminCtx = await browser.newContext();
  const driver = await driverCtx.newPage();
  const admin = await adminCtx.newPage();
  await login(driver, seed.users.driver);
  await login(admin, seed.users.admin);

  // Şoför: doğrudan "Seferi iptal et" yok, yalnız "İptal talebi".
  await driver.goto(`/seferler/${seed.ids.tripMorning}`);
  await expect(driver.getByRole("button", { name: "İptal talebi" })).toBeVisible();
  await expect(driver.getByRole("button", { name: "Seferi iptal et" })).toHaveCount(0);

  // Dialog erişilebilirliği: Esc ile kapanır, odak açan butona döner.
  await driver.getByRole("button", { name: "İptal talebi" }).focus();
  await driver.keyboard.press("Enter");
  await expect(driver.getByRole("dialog")).toBeVisible();
  await driver.keyboard.press("Escape");
  await expect(driver.getByRole("dialog")).toHaveCount(0);
  await expect(driver.getByRole("button", { name: "İptal talebi" })).toBeFocused();

  await driver.getByRole("button", { name: "İptal talebi" }).click();
  const dlg = driver.getByRole("dialog");
  await dlg.getByRole("button", { name: "Talebi gönder" }).click();
  await expect(dlg.getByText(/zorunludur/)).toBeVisible(); // gerekçe boş gönderilemez
  await dlg.getByRole("textbox").fill("Araç arıza lambası yandı, sefere çıkamıyoruz.");
  await dlg.getByRole("button", { name: "Talebi gönder" }).click();
  await expect(driver.getByText("İptal talebiniz yönetici kararını bekliyor")).toBeVisible();
  await expect(driver.getByRole("button", { name: "İptal talebi" })).toBeDisabled();
  // Sefer durumu değişmedi.
  await expect(driver.getByText("Planlandı").first()).toBeVisible();

  // ADMIN: Olaylar sekmesinde talep "Karar bekliyor"; gerekçeli ret → sefer devam eder.
  await admin.goto(`/seferler/${seed.ids.tripMorning}?tab=olaylar`);
  await expect(admin.getByText("Karar bekliyor").first()).toBeVisible();
  await admin.getByRole("button", { name: "Reddet" }).first().click();
  const resolveDlg = admin.getByRole("dialog");
  await resolveDlg.getByRole("textbox").fill("Yedek araç ayarlandı, sefer devam edecek.");
  await resolveDlg.getByRole("button", { name: "Reddet" }).click();
  await expect(admin.getByText("Reddedildi").first()).toBeVisible();
  await expect(admin.getByText("Planlandı").first()).toBeVisible();

  // ADMIN: diğer seferde doğrudan iptal — kırmızı kontrol + zorunlu gerekçe.
  await admin.goto(`/seferler/${seed.ids.tripAfternoon}`);
  await admin.getByRole("button", { name: "Seferi iptal et" }).first().click();
  const cancelDlg = admin.getByRole("dialog");
  await cancelDlg.getByRole("button", { name: "Seferi iptal et" }).click();
  await expect(cancelDlg.getByText(/zorunludur/)).toBeVisible();
  await cancelDlg.getByRole("textbox").fill("Okul tatil ilan etti.");
  await cancelDlg.getByRole("button", { name: "Seferi iptal et" }).click();
  await expect(admin.getByText("İptal edildi").first()).toBeVisible();
  await expect(admin.getByText("Okul tatil ilan etti.").first()).toBeVisible();

  // Şoför iptal edilen seferi görür; talep/aksiyon yok.
  await driver.goto(`/seferler/${seed.ids.tripAfternoon}`);
  await expect(driver.getByText("İptal edildi").first()).toBeVisible();
  await expect(driver.getByRole("button", { name: "Seferi iptal et" })).toHaveCount(0);

  await driverCtx.close();
  await adminCtx.close();
});
