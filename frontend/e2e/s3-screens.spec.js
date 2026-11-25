// @ts-check
// Sprint 3 — Değişen ekranların masaüstü (1440) ve mobil (390/360) görünümü: öğrenci detayı "Öğrenci hesabı"
// kartı (bağsız/bağlı), bağlama dialogu ve STUDENT "Bugünkü Servisim". Yatay taşma olmamalı; ekran görüntüleri
// docs/design/sprint3-screens/ altına yazılır.
const path = require("node:path");
const { test, expect } = require("@playwright/test");
const { login, loadSeed } = require("./support/helpers");

const OUT = path.join(__dirname, "..", "..", "docs", "design", "sprint3-screens");
const WIDTHS = [
  [1440, 900],
  [390, 844],
  [360, 780],
];

async function noOverflow(page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, "yatay taşma").toBeLessThanOrEqual(0);
}

// Paket koşusunun parçası değildir; yalnız E2E_SCREENS=1 ile ayrıca çalıştırılır (ekran görüntüsü üretir).
test.skip(process.env.E2E_SCREENS !== "1", "Ekran görüntüsü taraması yalnız E2E_SCREENS=1 ile çalışır.");

test("değişen ekranlar masaüstü ve mobilde taşmadan görünür", async ({ browser }) => {
  const seed = loadSeed();
  for (const [width, height] of WIDTHS) {
    const ctx = await browser.newContext({ viewport: { width, height } });
    const page = await ctx.newPage();
    await login(page, seed.users.admin);

    await page.goto(`/ogrenciler/${seed.ids.studentA}`);
    await expect(page.getByText(seed.users.student.email).first()).toBeVisible();
    await noOverflow(page);
    await page.screenshot({ path: path.join(OUT, `admin-ogrenci-hesap-bagli-${width}.png`), fullPage: true });

    await page.goto(`/ogrenciler/${seed.ids.studentB}`);
    await expect(page.getByText("Bağlı hesap yok").first()).toBeVisible();
    await noOverflow(page);
    await page.screenshot({ path: path.join(OUT, `admin-ogrenci-hesap-yok-${width}.png`), fullPage: true });
    await page.getByRole("button", { name: "Hesap bağla" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.getByRole("dialog").getByText("Yeni hesap oluştur", { exact: true }).click();
    await noOverflow(page);
    await page.screenshot({ path: path.join(OUT, `admin-hesap-bagla-dialog-${width}.png`) });
    await ctx.close();

    const sctx = await browser.newContext({ viewport: { width, height } });
    const student = await sctx.newPage();
    await login(student, seed.users.student);
    await expect(student.getByText(seed.names.studentA).filter({ visible: true }).first()).toBeVisible();
    await noOverflow(student);
    await student.screenshot({ path: path.join(OUT, `student-servisim-${width}.png`), fullPage: true });
    await sctx.close();
  }
});
