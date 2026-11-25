// @ts-check
// Sprint 3 — PrimeReact arayüzü için küçük Playwright yardımcıları.
const { expect } = require("@playwright/test");

/** `inputId`'li PrimeReact Dropdown'dan seçenek seçer (filtre kutusu varsa önce arar). */
async function choose(page, inputId, optionName, { search } = {}) {
  await page.locator(`.p-dropdown:has(#${inputId})`).click();
  const panel = page.locator(".p-dropdown-panel");
  await expect(panel).toBeVisible();
  const filterBox = panel.locator("input.p-dropdown-filter");
  if (await filterBox.count()) await filterBox.fill(search ?? optionName);
  await panel.getByRole("option", { name: optionName }).first().click();
  await expect(panel).toHaveCount(0);
}

/** Başarı/hata bildirimi (PrimeReact Toast). */
async function expectToast(page, text) {
  await expect(page.locator(".p-toast").getByText(text).first()).toBeVisible();
}

/** Açık dialogun birincil butonu. */
async function submitDialog(page, label = "Kaydet") {
  await page.getByRole("dialog").last().getByRole("button", { name: label, exact: true }).click();
}

/** Açık dialogu alt çubuktaki butonla kapatır (başlıktaki "Kapat" ikonundan ayrı). */
async function closeDialog(page, label = "Kapat") {
  await page.getByRole("dialog").last().locator(".p-dialog-footer").getByRole("button", { name: label, exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
}

/** Kaydet sonrası: dialog kapanmış ve URL'den ?yeni / ?duzenle parametresi kalkmış olmalı (yenilemede tekrar açılmasın). */
async function dialogClosed(page) {
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page).not.toHaveURL(/[?&](yeni|duzenle)=/);
}

/** Onay dialogunu (confirmAction) kabul eder. */
async function acceptConfirm(page, label) {
  const dlg = page.locator(".p-confirm-dialog");
  await expect(dlg).toBeVisible();
  await dlg.getByRole("button", { name: label, exact: true }).click();
}

/** Liste ekranının arama kutusuna yazar (gecikmeli filtre). */
async function search(page, label, text) {
  await page.getByLabel(label, { exact: true }).fill(text);
  await tableSettled(page);
}

/** Tablo yeniden yüklenirken (gecikmeli arama, yenileme) satır butonları bir an örtülür; yükleme bitene dek bekler. */
async function tableSettled(page) {
  await page.waitForTimeout(500); // arama gecikmesi (350 ms) dolsun
  await expect(page.locator(".p-datatable-loading-overlay")).toHaveCount(0);
}

/** Kısa, koşuya özgü etiket. */
// "I/i" bilerek yok: tr-TR yerelinde büyük/küçük harf dönüşümü (I↔ı, İ↔i) liste filtrelerinde eşleşmeyi bozabiliyor.
const TAG_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
function shortTag() {
  let out = "";
  for (let i = 0; i < 6; i++) out += TAG_ALPHABET[Math.floor(Math.random() * TAG_ALPHABET.length)];
  return out;
}

module.exports = { tableSettled, choose, expectToast, submitDialog, closeDialog, dialogClosed, acceptConfirm, search, shortTag };
