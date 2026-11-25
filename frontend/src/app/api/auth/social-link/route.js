import { readAuthCookies } from "@/lib/auth/authCookies";
import { callBackend, passthrough } from "@/lib/auth/proxyHandler";
import { isCsrfValid } from "@/lib/auth/csrf";
import { csrfForbidden, unauthorized, badGateway, apiErrorResponse } from "@/lib/auth/serverApiError";
import { OAUTH_PENDING_LINK_COOKIE, unpackPendingLink } from "@/lib/auth/oauth";

/**
 * POST /api/auth/social-link — docs/API.md §1, docs/SECURITY.md "Sosyal giriş" adım 5.
 * Authenticated + CSRF korumalı. İstemci BOŞ bir body gönderir — bağlanacak sosyal kimlik
 * (provider/providerUserId/email) istemci JS'ine HİÇ açılmaz, SADECE `/social/{provider}/callback`
 * adımında bırakılan HttpOnly+imzalı `ssp_oauth_pending_link` çerezinden sunucu tarafında okunur.
 * Oturum zaten "hesabın kontrolü" kanıtıdır (change-password ile AYNI desen) — ayrıca şifre istenmez.
 */
export async function POST(request) {
  const path = "/api/auth/social-link";
  if (!isCsrfValid(request, request.cookies.get("ssp_csrf")?.value)) {
    return csrfForbidden(path);
  }

  const { accessToken } = readAuthCookies(request);
  if (!accessToken) {
    return unauthorized(path);
  }

  const pending = unpackPendingLink(request.cookies.get(OAUTH_PENDING_LINK_COOKIE)?.value);
  if (!pending) {
    return apiErrorResponse({
      status: 400,
      error: "VALIDATION_ERROR",
      message: "Bağlanacak bir sosyal giriş isteği bulunamadı veya süresi doldu. Lütfen tekrar deneyin.",
      path,
    });
  }

  // Güvenlik: `pending` çerezi OAuth callback'i sırasında (oturum açmadan ÖNCE) yazılır - bu
  // isteğin, o akışı GERÇEKTEN tamamlamış kullanıcı tarafından yapıldığının tek kanıtı, bağlanacak
  // e-postanın şu an oturum açmış kullanıcının KENDİ e-postasıyla eşleşmesidir. Bu kontrol
  // olmadan, aynı tarayıcıda (paylaşılan/kiosk bilgisayar senaryosu) daha sonra FARKLI bir hesapla
  // giriş yapan biri, kendisine ait olmayan bir sosyal kimliği kendi hesabına bağlayabilirdi.
  let meRes;
  try {
    meRes = await callBackend("/api/auth/me", { method: "GET", accessToken });
  } catch {
    return badGateway(path);
  }
  if (!meRes.ok) {
    return unauthorized(path);
  }
  const me = await meRes.json().catch(() => null);
  if (!me?.email || me.email.toLowerCase() !== pending.email.toLowerCase()) {
    return apiErrorResponse({
      status: 409,
      error: "CONFLICT",
      message: "Bu sosyal giriş isteği başka bir e-posta için başlatılmış. Lütfen tekrar deneyin.",
      path,
    });
  }

  let backendRes;
  try {
    backendRes = await callBackend(path, {
      method: "POST",
      accessToken,
      body: JSON.stringify({
        provider: pending.provider,
        providerUserId: pending.providerUserId,
        email: pending.email,
      }),
    });
  } catch {
    return badGateway(path);
  }

  const response = await passthrough(backendRes);
  response.cookies.set({ name: OAUTH_PENDING_LINK_COOKIE, value: "", path: "/api/auth", maxAge: 0 });
  return response;
}
