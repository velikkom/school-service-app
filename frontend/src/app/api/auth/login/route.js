import { NextResponse } from "next/server";
import { setAuthCookies } from "@/lib/auth/authCookies";
import { callBackend, passthrough } from "@/lib/auth/proxyHandler";
import { apiErrorResponse, badGateway } from "@/lib/auth/serverApiError";

/**
 * POST /api/auth/login — docs/API.md §1.
 * Request body (aynen backend'e iletilir): { "email": "...", "password": "..." }
 * Backend response: { accessToken, refreshToken, user } — BFF bu token'ları HttpOnly cookie'ye
 * yazar, tarayıcıya SADECE `user` alanını döner. Token JSON olarak tarayıcıya asla iletilmez.
 * Ham fetch, lib/proxyHandler.js'teki callBackend'e delege edilir; başarısız response backend'in
 * kendi ApiError şekliyle passthrough edilir. Başarı durumunda token/cookie mantığı (kritik,
 * generic handler'a ZORLANMADI) bu route'ta kalır.
 */
export async function POST(request) {
  const path = "/api/auth/login";
  let body;
  try {
    body = await request.text();
  } catch {
    return apiErrorResponse({
      status: 400,
      error: "VALIDATION_ERROR",
      message: "Girilen bilgiler geçersiz.",
      path,
    });
  }

  let backendRes;
  try {
    backendRes = await callBackend(path, { method: "POST", body });
  } catch {
    return badGateway(path);
  }

  if (!backendRes.ok) {
    return await passthrough(backendRes);
  }

  const data = await backendRes.json().catch(() => null);
  if (!data) {
    return badGateway(path);
  }

  // rememberMe, cookie kalıcılığı kararı için body'den (zaten backend'e aynen iletildi) tekrar
  // okunur — ayrı bir round-trip veya ikinci bir kaynak DEĞİL, aynı request body'nin BFF tarafında
  // da ayrıştırılmış hali (bkz. docs/SECURITY.md "Beni hatırla").
  let rememberMe = false;
  try {
    rememberMe = Boolean(JSON.parse(body)?.rememberMe);
  } catch {
    // body zaten backend'e ulaşıp 2xx döndüğüne göre geçerli JSON'dur; savunma amaçlı yut.
  }

  const response = NextResponse.json({ user: data.user });
  setAuthCookies(
    response,
    {
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      refreshTokenExpiresAt: data.refreshTokenExpiresAt,
    },
    { rememberMe }
  );
  return response;
}
