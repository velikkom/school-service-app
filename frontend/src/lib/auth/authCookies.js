import {
  COOKIE_ACCESS_TOKEN,
  COOKIE_REFRESH_TOKEN,
  COOKIE_CSRF_TOKEN,
} from "@/lib/auth/cookieNames";
import { JWT_ACCESS_TOKEN_TTL_MINUTES, IS_PRODUCTION } from "@/lib/auth/env";
import { generateCsrfToken } from "@/lib/auth/csrf";

/**
 * docs/SECURITY.md cookie ayarları:
 * - ssp_at: HttpOnly, Secure(prod), SameSite=Lax, Path=/, TTL=access token ömrü (remember-me'den
 *   BAĞIMSIZ, her zaman sabit).
 * - ssp_rt: HttpOnly, Secure(prod), SameSite=Strict, Path=/api/auth — "Beni hatırla" İŞARETLİYSE
 *   backend'in döndüğü GERÇEK `refreshTokenExpiresAt`'tan türetilen `Max-Age` ile kalıcı; İŞARETLİ
 *   DEĞİLSE Max-Age/Expires HİÇ YAZILMAZ (tarayıcı oturum çerezi — kapanınca silinir).
 * - ssp_csrf: HttpOnly DEĞİL, Secure(prod), SameSite=Lax, Path=/ — kalıcılığı ssp_rt ile AYNI
 *   kaderi paylaşır (docs/SECURITY.md "Refresh token ve cookie sürelerini birbiriyle uyumlu tut").
 *
 * Bu dosya SADECE saf çerez okuma/yazma fonksiyonlarını içerir (backend'e fetch YAPMAZ).
 * Backend'e fetch yapan refreshAccessToken için bkz. @/lib/authSession.
 */
export function readAuthCookies(request) {
  return {
    accessToken: request.cookies.get(COOKIE_ACCESS_TOKEN)?.value,
    refreshToken: request.cookies.get(COOKIE_REFRESH_TOKEN)?.value,
    csrfToken: request.cookies.get(COOKIE_CSRF_TOKEN)?.value,
  };
}

/**
 * refreshTokenExpiresAt'tan (ISO string/Date) saniye cinsinden, geçmişte kalmayacak bir Max-Age
 * hesaplar — backend'in döndüğü değer tek doğruluk kaynağıdır, ayrı bir TTL sabiti DEĞİL.
 */
function maxAgeFromExpiry(expiresAt) {
  if (!expiresAt) return undefined;
  const seconds = Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000);
  return Math.max(seconds, 60);
}

/**
 * Login/refresh sonrası response üzerine auth cookie'lerini yazar.
 * refreshToken verilmezse (örn. refresh akışında backend rotasyon yapmıyorsa) ssp_rt dokunulmaz —
 * bu sayede login'de set edilen "Beni hatırla" çerez kalıcılığı refresh'ten ETKİLENMEZ.
 * `rememberMe`/`refreshTokenExpiresAt` SADECE refreshToken verildiğinde (yani login anında) anlamlıdır.
 */
export function setAuthCookies(
  response,
  { accessToken, refreshToken, refreshTokenExpiresAt },
  { rotateCsrf = true, rememberMe = false } = {}
) {
  if (accessToken) {
    response.cookies.set({
      name: COOKIE_ACCESS_TOKEN,
      value: accessToken,
      httpOnly: true,
      secure: IS_PRODUCTION,
      sameSite: "lax",
      path: "/",
      maxAge: JWT_ACCESS_TOKEN_TTL_MINUTES * 60,
    });
  }
  if (refreshToken) {
    const persistentMaxAge = rememberMe ? maxAgeFromExpiry(refreshTokenExpiresAt) : undefined;
    response.cookies.set({
      name: COOKIE_REFRESH_TOKEN,
      value: refreshToken,
      httpOnly: true,
      secure: IS_PRODUCTION,
      sameSite: "strict",
      path: "/api/auth",
      ...(persistentMaxAge !== undefined ? { maxAge: persistentMaxAge } : {}),
    });
  }
  if (rotateCsrf) {
    // Sadece login'de (refreshToken ile birlikte) rotateCsrf=true gelir, bu yüzden aynı
    // rememberMe kararını burada da uygulamak ssp_rt/ssp_csrf kalıcılığını tutarlı tutar.
    const csrfMaxAge = refreshToken && rememberMe ? maxAgeFromExpiry(refreshTokenExpiresAt) : undefined;
    response.cookies.set({
      name: COOKIE_CSRF_TOKEN,
      value: generateCsrfToken(),
      httpOnly: false,
      secure: IS_PRODUCTION,
      sameSite: "lax",
      path: "/",
      ...(csrfMaxAge !== undefined ? { maxAge: csrfMaxAge } : {}),
    });
  }
  return response;
}

export function clearAuthCookies(response) {
  for (const { name, path } of [
    { name: COOKIE_ACCESS_TOKEN, path: "/" },
    { name: COOKIE_REFRESH_TOKEN, path: "/api/auth" },
    { name: COOKIE_CSRF_TOKEN, path: "/" },
  ]) {
    response.cookies.set({
      name,
      value: "",
      httpOnly: name !== COOKIE_CSRF_TOKEN,
      secure: IS_PRODUCTION,
      sameSite: name === COOKIE_REFRESH_TOKEN ? "strict" : "lax",
      path,
      maxAge: 0,
    });
  }
  return response;
}
