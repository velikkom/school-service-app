import { BACKEND_INTERNAL_URL } from "@/lib/auth/env";

/**
 * Backend'e gerçek fetch yapan tek fonksiyon bu dosyadadır (mimari bulgu #13 ile ayrıştırıldı —
 * saf çerez okuma/yazma fonksiyonları için bkz. @/lib/authCookies).
 *
 * Refresh token'ı JSON body `{ "refreshToken": "..." }` ile backend'e gönderiyoruz — bu, backend
 * auth modülünün (tamamlandı, test edildi — bkz. docs/API.md §1, docs/TEST_REPORT.md) gerçek
 * POST /api/auth/refresh sözleşmesiyle doğrulanmış şekildir.
 */
export async function refreshAccessToken(refreshToken) {
  const res = await fetch(`${BACKEND_INTERNAL_URL}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
    cache: "no-store",
  });
  if (!res.ok) {
    return null;
  }
  const data = await res.json().catch(() => null);
  if (!data || !data.accessToken) return null;
  return { accessToken: data.accessToken, refreshToken: data.refreshToken };
}
