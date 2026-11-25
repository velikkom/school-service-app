import { NextResponse } from "next/server";
import { readAuthCookies, clearAuthCookies } from "@/lib/auth/authCookies";
import { refreshAccessToken } from "@/lib/auth/authSession";
import { callBackend } from "@/lib/auth/proxyHandler";
import { isCsrfValid } from "@/lib/auth/csrf";
import { csrfForbidden } from "@/lib/auth/serverApiError";

/**
 * POST /api/auth/logout — CSRF korumalı. Backend `/logout` geçerli bir access token ister; süresi
 * dolmuşsa önce refresh token ile yenilenip tekrar denenir ki refresh token sunucuda da iptal edilsin.
 * Backend sonucu ne olursa olsun çerezler temizlenir.
 */
export async function POST(request) {
  const path = "/api/auth/logout";
  if (!isCsrfValid(request, request.cookies.get("ssp_csrf")?.value)) {
    return csrfForbidden(path);
  }

  const { accessToken, refreshToken } = readAuthCookies(request);
  const body = JSON.stringify({ refreshToken });

  try {
    let res = accessToken ? await callBackend(path, { method: "POST", accessToken, body }) : null;
    if ((!res || res.status === 401) && refreshToken) {
      const refreshed = await refreshAccessToken(refreshToken).catch(() => null);
      if (refreshed) {
        res = await callBackend(path, { method: "POST", accessToken: refreshed.accessToken, body });
      }
    }
  } catch {
    // Backend'e ulaşılamasa da oturum yerel olarak sonlandırılır.
  }

  return clearAuthCookies(NextResponse.json({ message: "Çıkış yapıldı." }));
}
