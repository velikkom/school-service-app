// @ts-check
// Akış 1 — Veli "servise binmeyecek" bildirimi → saha yolcu listesi (15 sn polling) → ADMIN sefer
// detayı → yoklama öncesi geri alma. Gerçek backend + BFF, ayrı tarayıcı oturumları.
const { test, expect } = require("@playwright/test");
const { loadSeed, login } = require("./support/helpers");

const NOTE = "Doktor randevusu var, akşam servise binmeyecek.";

test("veli bildirimi sahada ve ADMIN'de görünür, yoklama öncesi geri alınır", async ({ browser }) => {
  const seed = loadSeed();
  const tripId = seed.ids.tripAfternoon;
  const A = seed.ids.studentA;
  const childName = seed.names.studentA;

  const parentCtx = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const hostessCtx = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const adminCtx = await browser.newContext();
  const parent = await parentCtx.newPage();
  const hostess = await hostessCtx.newPage();
  const admin = await adminCtx.newPage();
  await login(parent, seed.users.parent);
  await login(hostess, seed.users.hostess);
  await login(admin, seed.users.admin);

  // Saha ekranı veli bildiriminden ÖNCE açık: güncellemeyi polling ile görmeli.
  await hostess.goto(`/seferler/${tripId}?tab=yoklama`);
  const row = hostess.getByTestId(`roster-row-${A}`);
  await expect(row).toBeVisible();
  await expect(row.getByText("Veli: binmeyecek")).toHaveCount(0);

  // Veli: bugünkü servis → akşam kartında "Servise binmeyecek".
  await parent.goto("/servisim");
  await parent.getByRole("tab", { name: new RegExp(childName.split(" ").slice(-2, -1)[0]) }).click();
  const card = parent.getByRole("article", { name: "Dönüş (akşam) servisi" });
  await card.getByRole("button", { name: "Servise binmeyecek" }).click();
  const dlg = parent.getByRole("dialog");
  await dlg.getByLabel("Not (isteğe bağlı)").fill(NOTE);
  await dlg.getByRole("button", { name: "Bildirimi gönder" }).click();
  await expect(card.getByRole("button", { name: "Bildirimi geri al" })).toBeVisible();
  await expect(card.getByRole("button", { name: "Servise binmeyecek" })).toHaveCount(0);

  // Saha: sayfa yenilenmeden en geç bir polling turunda (15 sn) mor bildirim + not.
  const t0 = Date.now();
  await expect(row.getByText("Veli: binmeyecek")).toBeVisible({ timeout: 25_000 });
  test.info().annotations.push({ type: "polling", description: `Saha listesinde görünme süresi ≈ ${Math.round((Date.now() - t0) / 1000)} sn` });
  await expect(row).toContainText(NOTE);
  // Resmi yoklama ayrı: durum "İşlem yok".
  await expect(row.getByText("İşlem yok")).toBeVisible();

  // Sefer başlayınca "Bindi" aktif kalır (bildirim yoklamayı engellemez).
  await hostess.getByRole("button", { name: "Seferi başlat" }).first().click();
  await hostess.getByRole("alertdialog").or(hostess.getByRole("dialog")).getByRole("button", { name: "Seferi başlat" }).click();
  await expect(row.getByRole("button", { name: "Bindi" })).toBeEnabled();
  await expect(row.getByText("İşlem yok")).toBeVisible();

  // ADMIN: sefer detayı Yolcular ve Veli bildirimleri sekmeleri.
  await admin.goto(`/seferler/${tripId}?tab=yolcular`);
  await expect(admin.getByTestId(`roster-row-${A}`).getByText("Veli: binmeyecek")).toBeVisible();
  await admin.goto(`/seferler/${tripId}?tab=bildirimler`);
  await expect(admin.getByRole("main")).toContainText(NOTE);

  // Veli yoklama girilmeden geri alır.
  await card.getByRole("button", { name: "Bildirimi geri al" }).click();
  await parent.getByRole("alertdialog").or(parent.getByRole("dialog")).getByRole("button", { name: "Geri al" }).click();
  await expect(card.getByRole("button", { name: "Servise binmeyecek" })).toBeVisible();

  // Saha listesinden kalkar (polling).
  await expect(row.getByText("Veli: binmeyecek")).toHaveCount(0, { timeout: 25_000 });

  // Yoklama girildikten sonra veli geri alma/gönderme göremez.
  await row.getByRole("button", { name: "Bindi" }).click();
  await expect(row.getByText("Bindi", { exact: true }).first()).toBeVisible();
  await parent.reload();
  const cardAfter = parent.getByRole("article", { name: "Dönüş (akşam) servisi" });
  await expect(cardAfter).toBeVisible();
  await expect(cardAfter.getByRole("button", { name: "Servise binmeyecek" })).toHaveCount(0);
  await expect(cardAfter.getByRole("button", { name: "Bildirimi geri al" })).toHaveCount(0);

  await parentCtx.close();
  await hostessCtx.close();
  await adminCtx.close();
});
