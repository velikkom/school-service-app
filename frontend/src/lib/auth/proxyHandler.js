import { NextResponse } from "next/server";
import { BACKEND_INTERNAL_URL } from "@/lib/auth/env";
import { readAuthCookies, setAuthCookies, clearAuthCookies } from "@/lib/auth/authCookies";
import { refreshAccessToken } from "@/lib/auth/authSession";
import { isCsrfValid } from "@/lib/auth/csrf";
import { apiErrorResponse, unauthorized, csrfForbidden, badGateway } from "@/lib/auth/serverApiError";

/**
 * Tek bir backend domain path'ine (allowlist) proxy yapan route handler fabrikası.
 * docs/SECURITY.md: "BFF sadece önceden tanımlı backend path'lerine proxy yapar (allowlist);
 * istemciden gelen keyfi hedef URL kabul eden açık bir proxy yoktur."
 *
 * `backendBasePath` her domain route.js dosyasında SABİT bir string'tir (örn. "/api/students"),
 * istemciden gelen hiçbir değer bu prefix'i değiştiremez — sadece catch-all segment'ler
 * (`[...path]`) prefix'in SONRASINA eklenir.
 *
 * @param {string} backendBasePath örn. "/api/students"
 */
export function createDomainProxy(backendBasePath) {
  async function handle(request, context) {
    // Next.js 15+: route handler'larda `context.params` bir Promise'tir (sync erişim DAİMA
    // undefined döner) - await edilmeden okunursa `path` her zaman [] olur ve EVERY nested
    // (/{id}, /{id}/stops gibi) istek backend'in koleksiyon (liste) endpoint'ine gider.
    const resolvedParams = await context?.params;
    const path = Array.isArray(resolvedParams?.path) ? resolvedParams.path : [];
    const requestUrl = new URL(request.url);
    const backendPath = `${backendBasePath}${path.length ? "/" + path.map(encodeURIComponent).join("/") : ""}${requestUrl.search}`;

    if (!isCsrfValid(request, request.cookies.get("ssp_csrf")?.value)) {
      return csrfForbidden(requestUrl.pathname);
    }

    const { accessToken, refreshToken } = readAuthCookies(request);
    if (!accessToken) {
      return unauthorized(requestUrl.pathname);
    }

    let bodyText;
    if (!["GET", "HEAD", "DELETE"].includes(request.method)) {
      bodyText = await request.text();
    }
    const idempotencyKey = request.headers.get("idempotency-key");

    let backendRes;
    try {
      backendRes = await callBackend(backendPath, {
        method: request.method,
        accessToken,
        body: bodyText,
        idempotencyKey,
      });
    } catch {
      return badGateway(requestUrl.pathname);
    }

    if (backendRes.status !== 401) {
      return await passthrough(backendRes);
    }

    // Access token süresi dolmuş olabilir — otomatik refresh dene (docs/SECURITY.md).
    if (!refreshToken) {
      // ssp_rt çerezi Path=/api/auth olduğu için domain isteklerinde gelmez. Çerezler burada
      // SİLİNMEZ: istemci 401'de /api/auth/refresh çağırıp isteği bir kez tekrarlar (lib/http/client.js).
      return unauthorized(requestUrl.pathname);
    }

    const refreshed = await refreshAccessToken(refreshToken).catch(() => null);
    if (!refreshed) {
      return clearAuthCookies(unauthorized(requestUrl.pathname));
    }

    let retryRes;
    try {
      retryRes = await callBackend(backendPath, {
        method: request.method,
        accessToken: refreshed.accessToken,
        body: bodyText,
        idempotencyKey,
      });
    } catch {
      return badGateway(requestUrl.pathname);
    }

    const finalResponse = await passthrough(retryRes);
    setAuthCookies(
      finalResponse,
      { accessToken: refreshed.accessToken, refreshToken: refreshed.refreshToken },
      { rotateCsrf: false }
    );
    return finalResponse;
  }

  return { GET: handle, POST: handle, PATCH: handle, PUT: handle, DELETE: handle };
}

export async function callBackend(path, { method, accessToken, body, idempotencyKey }) {
  const headers = {};
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;

  return fetch(`${BACKEND_INTERNAL_URL}${path}`, {
    method,
    headers,
    body,
    cache: "no-store",
  });
}

// Fetch/Response spesifikasyonu: 204/205/304 durum kodları "null body" statüsüdür — bu durumlarda
// Response constructor'ına body olarak boş STRING ("") verilirse (null/undefined DEĞİL) hata
// fırlatır ("Response constructor: Invalid response status code 204" — Node'un fetch/undici
// implementasyonunda doğrulandı). Backend bu kodları `ResponseEntity.noContent()` ile sıkça döner
// (örn. change-password, reset-password, bu turda eklenen register-requests/verify-email) — bu
// kontrol olmadan HER 204/304 passthrough'u BFF'te yakalanmamış bir hataya (500, boş body) düşerdi.
const NULL_BODY_STATUSES = new Set([204, 205, 304]);

export async function passthrough(backendRes) {
  const text = await backendRes.text();
  const headers = new Headers();
  const contentType = backendRes.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  // Finans belgeleri (makbuz/ekstre) `Content-Disposition: attachment; filename="..."` ile döner —
  // tarayıcı tarafındaki indirme yardımcısı (lib/download.js) dosya adını bu başlıktan okur.
  const contentDisposition = backendRes.headers.get("content-disposition");
  if (contentDisposition) headers.set("content-disposition", contentDisposition);
  const body = NULL_BODY_STATUSES.has(backendRes.status) ? null : text;
  return new NextResponse(body, { status: backendRes.status, headers });
}

export { apiErrorResponse };
