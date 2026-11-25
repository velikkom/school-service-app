// @ts-check
// Sprint 3 — Finans kayıtları gerçek API ile: sözleşme oluştur → tahakkuk üret (tekrarında çift borç yok) →
// kısmi ödeme → kalan borçtan fazla ödeme reddedilir (409) → öğrenci ekstresi (ekran + belge) aynı öğrenci
// için ödenen ve kalan tutarı doğru gösterir.
const { test, expect } = require("@playwright/test");
const { login, loadSeed } = require("./support/helpers");
const { adminApi } = require("./support/e2eEnv");
const { todayIstanbul } = require("./support/fixtures");
const { choose, expectToast, submitDialog, closeDialog, dialogClosed, shortTag } = require("./support/ui");

test.describe.configure({ mode: "serial" });

const money = (text) => new RegExp(text.replace(".", "\\.").replace(",", ",") + "\\s*TL");

test("finans: sözleşme, tahakkuk, kısmi ödeme, fazla ödeme reddi ve ekstre", async ({ page }) => {
  const seed = loadSeed();
  const tag = shortTag();
  const api = await adminApi();
  const student = await api("POST", "/api/students", { fullName: `S3 Finans Öğrencisi ${tag}`, birthDate: "2015-01-20", grade: "5" });
  await login(page, seed.users.admin);

  // ── Sözleşme oluştur ──
  await page.goto("/sozlesmeler");
  await page.getByRole("button", { name: "Sözleşme oluştur" }).first().click();
  const dlg = page.getByRole("dialog");
  await choose(page, "ct-student", student.fullName);
  await dlg.getByLabel("Başlangıç").fill(todayIstanbul());
  await dlg.getByLabel("Aylık ücret (TL)").fill("1200");
  await submitDialog(page);
  await expectToast(page, "Sözleşme oluşturuldu");
  await dialogClosed(page);
  await page.reload();
  const contractRow = page.getByRole("row", { name: new RegExp(student.fullName) });
  await expect(contractRow).toBeVisible();
  await expect(contractRow.getByText(money("1.200,00"))).toBeVisible();

  // ── Tahakkuk üret ──
  await page.goto("/tahakkuklar");
  await page.getByRole("button", { name: "Tahakkuk çalıştır" }).click();
  await submitDialog(page, "Çalıştır");
  let result = page.getByRole("dialog");
  await expect(result.getByText("Tahakkuk tamamlandı")).toBeVisible();
  const created1 = Number(await result.locator(".mono").nth(0).textContent());
  expect(created1).toBeGreaterThanOrEqual(1);
  await closeDialog(page);

  // Tekrar çalıştırma: yeni tahakkuk yok (hepsi atlanır).
  await page.getByRole("button", { name: "Tahakkuk çalıştır" }).click();
  await submitDialog(page, "Çalıştır");
  result = page.getByRole("dialog");
  await expect(result.getByText("Tahakkuk tamamlandı")).toBeVisible();
  await expect(result.locator(".mono").nth(0)).toHaveText("0");
  expect(Number(await result.locator(".mono").nth(1).textContent())).toBeGreaterThanOrEqual(created1);
  await closeDialog(page);
  const charges = await api("GET", `/api/billing/charges?studentId=${student.id}`);
  expect(charges.content ?? charges).toHaveLength(1);

  // ── Kısmi ödeme ──
  await page.goto(`/odemeler?yeni=1&ogrenci=${student.id}`);
  let pay = page.getByRole("dialog");
  await expect(pay.getByText(money("1.200,00"))).toBeVisible(); // "Kalan borç 1.200,00 TL"
  await pay.getByLabel("Tutar (TL)").fill("450");
  await submitDialog(page, "Ödemeyi kaydet");
  await expectToast(page, "Ödeme kaydedildi");
  await dialogClosed(page);

  // ── Fazla ödeme girişimi (kalan 750,00) → 409, kayıt oluşmaz ──
  await page.goto(`/odemeler?yeni=1&ogrenci=${student.id}`);
  pay = page.getByRole("dialog");
  await expect(pay.getByText(money("750,00"))).toBeVisible();
  await pay.getByLabel("Tutar (TL)").fill("900");
  await submitDialog(page, "Ödemeyi kaydet");
  await expect(pay.getByText("Tutar kalan borçtan fazla olabilir")).toBeVisible();
  await pay.getByRole("button", { name: "Vazgeç" }).click();
  const payments = await api("GET", `/api/payments?studentId=${student.id}`);
  expect((payments.content ?? payments).map((p) => p.amount)).toEqual(["450.00"]);

  // ── Öğrenci ekstresi: aynı öğrenci için ödenen 450, kalan 750 ──
  await page.goto(`/ogrenciler/${student.id}?tab=finans`);
  await page.reload();
  const debt = page.locator("text=Güncel borç").locator("xpath=..");
  await expect(debt.getByText(money("750,00"))).toBeVisible();
  await expect(page.getByText("Tahakkuk", { exact: true }).first()).toBeVisible();
  await expect(page.getByText(money("−450,00")).first()).toBeVisible();
  await expect(page.getByText(money("1.200,00")).first()).toBeVisible();

  const [doc] = await Promise.all([page.waitForEvent("popup"), page.getByRole("button", { name: "Ekstreyi aç / yazdır" }).click()]);
  await doc.waitForURL(/^blob:/);
  await doc.waitForLoadState();
  const docText = await doc.locator("body").innerText();
  expect(docText).toContain(student.fullName);
  expect(docText).toMatch(/1\.200,00/);
  expect(docText).toMatch(/450,00/);
  expect(docText).toMatch(/750,00/);
  await doc.close();

  // Ekran ve API aynı bakiyeyi söyler.
  const statement = await api("GET", `/api/students/${student.id}/statement`);
  expect(statement.currentBalance).toBe("750.00");
});
