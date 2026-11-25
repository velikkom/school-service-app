import { readAuthCookies } from "@/lib/auth/authCookies";
import { callBackend, passthrough } from "@/lib/auth/proxyHandler";
import { isCsrfValid } from "@/lib/auth/csrf";
import { csrfForbidden, unauthorized, badGateway } from "@/lib/auth/serverApiError";

/**
 * POST /api/auth/change-password — docs/API.md §1. Authenticated + CSRF korumalı.
 * Body: { "currentPassword": "...", "newPassword": "..." }
 * Bu modül kritik: backend auth hazır olduğunda GERÇEK bağlanmıştır, mock YOKTUR.
 * Ham fetch + hata şekli dönüştürme, lib/proxyHandler.js'teki callBackend/passthrough'a
 * delege edilir; cookie/CSRF mantığı bu route'ta kalır.
 */
export async function POST(request) {
  const path = "/api/auth/change-password";
  if (!isCsrfValid(request, request.cookies.get("ssp_csrf")?.value)) {
    return csrfForbidden(path);
  }

  const { accessToken } = readAuthCookies(request);
  if (!accessToken) {
    return unauthorized(path);
  }

  const body = await request.text();
  let backendRes;
  try {
    backendRes = await callBackend(path, { method: "POST", accessToken, body });
  } catch {
    return badGateway(path);
  }

  return await passthrough(backendRes);
}
