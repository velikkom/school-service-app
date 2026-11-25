// @ts-check
// Sprint 3 — Yönetim kayıtları gerçek API ile: öğrenci oluştur/düzenle, araç oluştur/düzenle, güzergâh +
// durak oluştur/düzenle, öğrenci güzergâh/durak ataması. Her başarı mesajından sonra sayfa yenilenir ve kayıt
// sunucudan tekrar okunur.
const { test, expect } = require("@playwright/test");
const { login, loadSeed } = require("./support/helpers");
const { todayIstanbul, plate } = require("./support/fixtures");
const { choose, expectToast, submitDialog, dialogClosed, search, shortTag } = require("./support/ui");

test.describe.configure({ mode: "serial" });

test("yönetim: öğrenci, araç, güzergâh/durak ve atama kayıtları yenilemeden sonra okunur", async ({ page }) => {
  const seed = loadSeed();
  const tag = shortTag();
  const studentName = `S3 Yönetim Öğrencisi ${tag}`;
  const routeName = `S3 Yönetim Güzergâhı ${tag}`;
  const vehiclePlate = plate();
  await login(page, seed.users.admin);

  // ── Öğrenci oluştur ──
  await page.goto("/ogrenciler");
  await page.getByRole("button", { name: "Yeni öğrenci" }).first().click();
  let dlg = page.getByRole("dialog");
  await dlg.getByLabel("Ad soyad").fill(studentName);
  await dlg.getByLabel("Doğum tarihi").fill("2016-04-12");
  await choose(page, "student-school", `S3 Test Okulu ${seed.tag}`);
  await dlg.getByLabel("Sınıf").fill("3-A");
  await submitDialog(page);
  await expectToast(page, "Öğrenci eklendi");
  await dialogClosed(page);
  await page.reload();
  await search(page, "Ara", studentName);
  const studentRow = page.getByRole("row", { name: new RegExp(studentName) });
  await expect(studentRow).toBeVisible();
  await expect(studentRow.getByText(/3-A/)).toBeVisible();

  // ── Öğrenci düzenle ──
  await studentRow.getByRole("button", { name: "Düzenle" }).click();
  dlg = page.getByRole("dialog");
  await expect(dlg.getByLabel("Ad soyad")).toHaveValue(studentName);
  await dlg.getByLabel("Sınıf").fill("3-B");
  await submitDialog(page);
  await expectToast(page, "Öğrenci güncellendi");
  await dialogClosed(page);
  await page.reload();
  await search(page, "Ara", studentName);
  await expect(page.getByRole("row", { name: new RegExp(studentName) }).getByText(/3-B/)).toBeVisible();

  // ── Araç oluştur / düzenle ──
  await page.goto("/araclar");
  await page.getByRole("button", { name: "Yeni araç" }).first().click();
  dlg = page.getByRole("dialog");
  await dlg.getByLabel("Plaka").fill(vehiclePlate);
  await dlg.getByLabel("Kapasite (kişi)").fill("18");
  await dlg.getByLabel("Model").fill("S3 Midibüs");
  await submitDialog(page);
  await expectToast(page, "Araç eklendi");
  await dialogClosed(page);
  await page.reload();
  await search(page, "Plaka ara", vehiclePlate);
  const vehicleRow = page.getByRole("row", { name: new RegExp(vehiclePlate.replace(/ /g, "\\s*")) });
  await expect(vehicleRow).toBeVisible();
  await expect(vehicleRow.getByText("S3 Midibüs")).toBeVisible();
  await vehicleRow.getByRole("button", { name: "Düzenle" }).click();
  dlg = page.getByRole("dialog");
  await dlg.getByLabel("Model").fill("S3 Midibüs Yeni");
  await dlg.getByLabel("Kapasite (kişi)").fill("20");
  await submitDialog(page);
  await expectToast(page, "Araç güncellendi");
  await dialogClosed(page);
  await page.reload();
  await search(page, "Plaka ara", vehiclePlate);
  await expect(page.getByRole("row", { name: new RegExp(vehiclePlate.replace(/ /g, "\\s*")) }).getByText("S3 Midibüs Yeni")).toBeVisible();

  // ── Güzergâh oluştur / düzenle + durak oluştur / düzenle ──
  await page.goto("/guzergahlar");
  await page.getByRole("button", { name: "Yeni güzergâh" }).first().click();
  dlg = page.getByRole("dialog");
  await dlg.getByLabel("Güzergâh adı").fill(routeName);
  await dlg.getByText("Gidiş (sabah)", { exact: true }).click();
  await submitDialog(page);
  await expectToast(page, "Güzergâh eklendi");
  await dialogClosed(page);
  await page.reload();
  await search(page, "Ara", routeName);
  await page.getByRole("link", { name: routeName }).click();
  await expect(page).toHaveURL(/\/guzergahlar\/\d+/);

  await page.getByRole("button", { name: "Durak ekle" }).first().click();
  dlg = page.getByRole("dialog");
  await dlg.getByLabel("Durak adı").fill(`Yönetim Durağı ${tag}`);
  await dlg.getByLabel("Sıra no").fill("1");
  await dlg.getByLabel("Planlanan saat").fill("07:45");
  await submitDialog(page);
  await expectToast(page, "Durak eklendi");
  await dialogClosed(page);
  await page.reload();
  const stopRow = page.getByRole("row", { name: new RegExp(`Yönetim Durağı ${tag}`) });
  await expect(stopRow).toBeVisible();
  await stopRow.getByRole("button", { name: "Düzenle" }).click();
  dlg = page.getByRole("dialog");
  await dlg.getByLabel("Durak adı").fill(`Yönetim Durağı ${tag} B`);
  await dlg.getByLabel("Planlanan saat").fill("07:50");
  await submitDialog(page);
  await expectToast(page, "Durak güncellendi");
  await dialogClosed(page);

  await page.getByRole("button", { name: "Düzenle" }).first().click();
  dlg = page.getByRole("dialog");
  await dlg.getByLabel("Güzergâh adı").fill(`${routeName} Düzenlendi`);
  await submitDialog(page);
  await expectToast(page, "Güzergâh güncellendi");
  await dialogClosed(page);
  await page.reload();
  await expect(page.getByText(`${routeName} Düzenlendi`).first()).toBeVisible();
  await expect(page.getByText(`Yönetim Durağı ${tag} B`)).toBeVisible();
  await expect(page.getByText("07:50").first()).toBeVisible();

  // ── Öğrenci güzergâh/durak ataması ──
  await page.goto("/atamalar");
  await page.getByRole("button", { name: "Yeni atama" }).first().click();
  dlg = page.getByRole("dialog");
  await choose(page, "as-student", studentName);
  await dlg.getByText("Gidiş (sabah)", { exact: true }).click();
  await choose(page, "as-route", `${routeName} Düzenlendi`);
  await choose(page, "as-stop", `Yönetim Durağı ${tag} B`);
  await dlg.getByLabel("Başlangıç").fill(todayIstanbul());
  await submitDialog(page);
  await expectToast(page, "Atama oluşturuldu");
  await dialogClosed(page);

  // Öğrenci detayından (yenilenmiş sayfa) atama sunucudan okunur.
  await page.goto("/ogrenciler");
  await search(page, "Ara", studentName);
  await page.getByRole("link", { name: studentName }).click();
  await expect(page).toHaveURL(/\/ogrenciler\/\d+$/);
  await page.goto(`${page.url()}?tab=atamalar`);
  await expect(page.getByRole("link", { name: `${routeName} Düzenlendi` })).toBeVisible();
  await expect(page.getByText(`Yönetim Durağı ${tag} B`)).toBeVisible();
});
