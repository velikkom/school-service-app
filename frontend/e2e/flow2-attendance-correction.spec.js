// @ts-check
// Akış 2 — Yoklama düzeltme: saha talebi → kayıt değişmeden görünür → ADMIN onay / gerekçeli ret →
// talep sahibinde sonuç + bildirim; eski kayıt çakışmasında 409 mesajı. Gerçek backend + BFF.
const { test, expect } = require("@playwright/test");
const { loadSeed, login, bff } = require("./support/helpers");

test("yoklama düzeltme talebi, ADMIN kararı ve eski kayıt 409", async ({ browser }) => {
  const seed = loadSeed();
  const tripId = seed.ids.tripMorning;
  const A = seed.ids.studentA;
  const B = seed.ids.studentB;

  const hostessCtx = await browser.newContext();
  const adminCtx = await browser.newContext();
  const hostess = await hostessCtx.newPage();
  const admin = await adminCtx.newPage();
  await login(hostess, seed.users.hostess);
  await login(admin, seed.users.admin);

  // Saha: seferi başlat (arayüzden) ve resmi yoklama gir.
  await hostess.goto(`/seferler/${tripId}`);
  await hostess.getByRole("button", { name: "Seferi başlat" }).first().click();
  await hostess.getByRole("alertdialog").or(hostess.getByRole("dialog")).getByRole("button", { name: "Seferi başlat" }).click();
  const rowA = hostess.getByTestId(`roster-row-${A}`);
  const rowB = hostess.getByTestId(`roster-row-${B}`);
  await rowA.getByRole("button", { name: "Gelmedi" }).click();
  await hostess.getByRole("dialog").getByRole("button", { name: "Gelmedi kaydet" }).click();
  await expect(rowA.getByText("Gelmedi", { exact: true }).first()).toBeVisible();
  await rowB.getByRole("button", { name: "Bindi" }).click();
  await expect(rowB.getByText("Bindi", { exact: true }).first()).toBeVisible();

  // Saha: A için "Bindi" düzeltmesi iste. Kayıt değişmez, "Düzeltme bekliyor" çıkar.
  await rowA.getByRole("button", { name: "Düzeltme iste" }).click();
  const dlg = hostess.getByRole("dialog");
  await dlg.locator(".p-dropdown").click();
  await hostess.getByRole("option", { name: "Bindi" }).click();
  await dlg.getByLabel("Gerekçe").fill("Öğrenci araca bindi, yanlışlıkla gelmedi işaretlendi.");
  await dlg.getByRole("button", { name: "Talebi gönder" }).click();
  await expect(rowA.getByText("Düzeltme bekliyor")).toBeVisible();
  await expect(rowA.getByText("Gelmedi", { exact: true }).first()).toBeVisible();
  await expect(rowA.getByRole("button", { name: "Düzeltme iste" })).toHaveCount(0);

  // Saha: B için "Gelmedi" düzeltmesi iste (sonra kayıt değişecek → eski veri).
  await rowB.getByRole("button", { name: "Düzeltme iste" }).click();
  await hostess.getByRole("dialog").locator(".p-dropdown").click();
  await hostess.getByRole("option", { name: "Gelmedi" }).click();
  await hostess.getByRole("dialog").getByLabel("Gerekçe").fill("Öğrenci binmedi.");
  await hostess.getByRole("dialog").getByRole("button", { name: "Talebi gönder" }).click();
  await expect(rowB.getByText("Düzeltme bekliyor")).toBeVisible();

  // ADMIN kuyruğu: iki talep kaynak → hedef durumla görünür.
  await admin.goto("/yoklama-duzeltmeleri");
  const corrections = (await bff(admin, "GET", `/attendance-corrections?status=PENDING&tripId=${tripId}`)).body.content;
  const corrA = corrections.find((c) => c.studentId === A);
  const corrB = corrections.find((c) => c.studentId === B);
  const cardA = admin.getByTestId(`correction-${corrA.id}`);
  const cardB = admin.getByTestId(`correction-${corrB.id}`);
  await expect(cardA).toBeVisible();
  await expect(cardB).toBeVisible();
  await expect(cardB.getByRole("button", { name: "Onayla" })).toBeEnabled();

  // Kuyruk açıkken saha B için iniş kaydeder → talep eski veriye dayanır.
  const drop = await bff(hostess, "POST", `/trips/${tripId}/attendance-events/dropoffs`, { studentId: B });
  expect(drop.status).toBe(200);

  // ADMIN eski ekrandan B'yi onaylamaya çalışır → 409 mesajı dialogda.
  await cardB.getByRole("button", { name: "Onayla" }).click();
  await admin.getByRole("dialog").getByRole("button", { name: "Onayla ve uygula" }).click();
  await expect(admin.getByRole("dialog").getByText(/Onaylanamadı|değişmiş/).first()).toBeVisible();
  await admin.getByRole("dialog").getByRole("button", { name: "Vazgeç" }).click();
  await admin.reload();
  await expect(cardB.getByText("Kayıt değişmiş")).toBeVisible();
  await expect(cardB.getByRole("button", { name: "Onayla" })).toBeDisabled();

  // ADMIN A'yı onaylar.
  await cardA.getByRole("button", { name: "Onayla" }).click();
  await admin.getByRole("dialog").getByLabel("Karar notu (isteğe bağlı)").fill("Kamera kaydıyla doğrulandı.");
  await admin.getByRole("dialog").getByRole("button", { name: "Onayla ve uygula" }).click();
  await expect(admin.getByRole("dialog")).toHaveCount(0);

  // ADMIN B'yi gerekçesiz reddedemez; gerekçeyle reddeder.
  await cardB.getByRole("button", { name: "Reddet" }).click();
  const rejectDlg = admin.getByRole("dialog");
  await rejectDlg.getByRole("button", { name: "Reddet" }).click();
  await expect(rejectDlg.getByRole("alert")).toContainText("zorunlu");
  await rejectDlg.getByLabel(/Ret gerekçesi/).fill("Kayıt değişti, yeniden talep açın.");
  await rejectDlg.getByRole("button", { name: "Reddet" }).click();
  await expect(admin.getByRole("dialog")).toHaveCount(0);

  // ADMIN sefer detayında A artık "Bindi".
  await admin.goto(`/seferler/${tripId}?tab=yolcular`);
  await expect(admin.getByTestId(`roster-row-${A}`).getByText("Bindi", { exact: true }).first()).toBeVisible();

  // Talep sahibi: sonuçlar ve karar notları (sayfa açılışında yeniden çekilir; açık ekranda 60 sn polling).
  await hostess.goto("/yoklama-duzeltmeleri");
  await expect(hostess.getByTestId(`correction-${corrA.id}`).getByText("Onaylandı")).toBeVisible();
  await expect(hostess.getByTestId(`correction-${corrA.id}`)).toContainText("Kamera kaydıyla doğrulandı.");
  await expect(hostess.getByTestId(`correction-${corrB.id}`).getByText("Reddedildi")).toBeVisible();
  await expect(hostess.getByTestId(`correction-${corrB.id}`)).toContainText("Kayıt değişti, yeniden talep açın.");

  // Saha yolcu listesi (15 sn polling) yeni durumu gösterir.
  await hostess.goto(`/seferler/${tripId}?tab=yoklama`);
  await expect(hostess.getByTestId(`roster-row-${A}`).getByText("Bindi", { exact: true }).first()).toBeVisible();

  // Bildirim talep sahibine ulaşır.
  await hostess.goto("/bildirimler");
  await expect(hostess.getByRole("main")).toContainText(/düzeltme/i);

  await hostessCtx.close();
  await adminCtx.close();
});
