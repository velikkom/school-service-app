// @ts-check
// Herkese açık akışlar: şifremi unuttum (her zaman 202), geçersiz e-posta doğrulama ve şifre sıfırlama
// bağlantıları, kayıt talebi doğrulaması, hatalı giriş mesajı. Gerçek backend + BFF.
const { test, expect } = require("@playwright/test");

test.use({ viewport: { width: 390, height: 844 } });

test("herkese açık giriş akışları", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("E-posta").fill("yok.boyle.biri@example.test");
  await page.locator("#login-password").fill("YanlisSifre123");
  await page.getByRole("button", { name: "Giriş yap" }).click();
  await expect(page.getByText("E-posta veya şifre hatalı.")).toBeVisible();

  await page.goto("/sifremi-unuttum");
  await page.getByLabel("E-posta").fill("yok.boyle.biri@example.test");
  await page.getByRole("button", { name: "Sıfırlama bağlantısı gönder" }).click();
  await expect(page.getByText("E-postanızı kontrol edin")).toBeVisible();

  await page.goto("/e-posta-dogrula?token=gecersiz-token");
  await expect(page.getByText("Doğrulama bağlantısı geçersiz")).toBeVisible();

  await page.goto("/sifre-sifirla");
  await expect(page.getByText("Bağlantı geçersiz")).toBeVisible();

  await page.goto("/kayit-talebi");
  await page.getByRole("button", { name: "Talep gönder" }).click();
  await expect(page.getByText("Ad zorunludur.", { exact: true })).toBeVisible();
  await expect(page.getByText("Şifre en az 8 karakter olmalıdır.")).toBeVisible();

  // Oturumsuz korumalı sayfa → giriş + dönüş adresi.
  await page.goto("/seferler");
  await expect(page).toHaveURL(/\/login\?from=%2Fseferler/);
});
