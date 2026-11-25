import { NextResponse } from "next/server";
import { readAuthCookies, setAuthCookies, clearAuthCookies } from "@/lib/auth/authCookies";
import { refreshAccessToken } from "@/lib/auth/authSession";
import { isCsrfValid } from "@/lib/auth/csrf";
import { csrfForbidden, unauthorized } from "@/lib/auth/serverApiError";

/**
 * POST /api/auth/refresh — docs/API.md §1. ssp_rt cookie'si ile yeni access token alır.
 * Normalde domain proxy route'ları 401 aldığında bu akışı KENDİ İÇİNDE (lib/proxyHandler.js)
 * otomatik tetikler; bu endpoint ayrıca istemcinin (örn. uygulama açılışında) manuel/silent
 * refresh tetiklemesi için dışa açıktır.
 */
export async function POST(request) {
  const path = "/api/auth/refresh";
  if (!isCsrfValid(request, request.cookies.get("ssp_csrf")?.value)) {
    return csrfForbidden(path);
  }

  const { refreshToken } = readAuthCookies(request);
  if (!refreshToken) {
    return clearAuthCookies(unauthorized(path));
  }

  const refreshed = await refreshAccessToken(refreshToken).catch(() => null);
  if (!refreshed) {
    return clearAuthCookies(unauthorized(path));
  }

  const response = NextResponse.json({ message: "Oturum yenilendi." });
  setAuthCookies(
    response,
    { accessToken: refreshed.accessToken, refreshToken: refreshed.refreshToken },
    { rotateCsrf: false }
  );
  return response;
}
