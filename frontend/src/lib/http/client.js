import { ApiError } from "@/lib/http/apiError";

const CSRF_COOKIE_NAME = "ssp_csrf";
const CSRF_HEADER_NAME = "X-CSRF-Token";
const UNAUTHORIZED_EVENT = "ssp:unauthorized";

function readCookie(name) {
  if (typeof document === "undefined") return undefined;
  const match = document.cookie.split("; ").find((row) => row.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.split("=").slice(1).join("=")) : undefined;
}

function safeJsonParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

let refreshInFlight = null;

/** Access token süresi dolduğunda tek bir yenileme isteği (eşzamanlı 401'ler paylaşır). */
function refreshSession() {
  if (!refreshInFlight) {
    const headers = new Headers();
    const csrf = readCookie(CSRF_COOKIE_NAME);
    if (csrf) headers.set(CSRF_HEADER_NAME, csrf);
    refreshInFlight = fetch("/api/auth/refresh", { method: "POST", headers, credentials: "same-origin", cache: "no-store" })
      .then((r) => r.ok)
      .catch(() => false)
      .finally(() => {
        setTimeout(() => {
          refreshInFlight = null;
        }, 0);
      });
  }
  return refreshInFlight;
}

/**
 * Tarayıcıdan yalnızca aynı origin BFF'e (`/api/...`) istek atan ortak istemci.
 * Token'lar HttpOnly çerezdedir; JS yalnızca CSRF çerezini okuyup başlığa koyar.
 * @param {string} path "/students" biçiminde (önüne /api eklenir)
 * @param {{ method?: string, json?: unknown, headers?: HeadersInit, signal?: AbortSignal }} [options]
 */
export async function request(path, options = {}) {
  const { json, headers, method = "GET", signal, _retried = false } = options;
  const finalHeaders = new Headers(headers);
  let body;
  if (json !== undefined) {
    finalHeaders.set("Content-Type", "application/json");
    body = JSON.stringify(json);
  }
  if (!["GET", "HEAD"].includes(method.toUpperCase())) {
    const csrf = readCookie(CSRF_COOKIE_NAME);
    if (csrf) finalHeaders.set(CSRF_HEADER_NAME, csrf);
  }

  let res;
  try {
    res = await fetch(`/api${path}`, { method, headers: finalHeaders, body, signal, credentials: "same-origin", cache: "no-store" });
  } catch (err) {
    if (err?.name === "AbortError") throw err;
    throw new ApiError({ status: 0, error: "NETWORK_ERROR", message: "Sunucuya ulaşılamadı. Bağlantınızı kontrol edin.", path });
  }

  const text = await res.text();
  const data = text ? safeJsonParse(text) : null;

  if (res.status === 401 && !_retried && !path.startsWith("/auth/")) {
    if (await refreshSession()) return request(path, { ...options, _retried: true });
  }

  if (!res.ok) {
    if (res.status === 401 && typeof window !== "undefined" && !path.startsWith("/auth/login")) {
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
    throw new ApiError(
      data && typeof data === "object"
        ? { ...data, status: data.status ?? res.status }
        : { status: res.status, error: "UNKNOWN_ERROR", message: "Beklenmeyen bir hata oluştu.", path }
    );
  }
  return data;
}

export const http = {
  get: (path, options) => request(path, { ...options, method: "GET" }),
  post: (path, json, options) => request(path, { ...options, method: "POST", json }),
  patch: (path, json, options) => request(path, { ...options, method: "PATCH", json }),
  put: (path, json, options) => request(path, { ...options, method: "PUT", json }),
  del: (path, options) => request(path, { ...options, method: "DELETE" }),
};

export { UNAUTHORIZED_EVENT };
