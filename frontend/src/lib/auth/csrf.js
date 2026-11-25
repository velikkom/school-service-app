import crypto from "node:crypto";
import { BFF_COOKIE_SECRET } from "@/lib/auth/env";

const CSRF_HEADER_NAME = "x-csrf-token";

/**
 * docs/SECURITY.md: double-submit CSRF token.
 * ssp_csrf cookie'si HttpOnly DEĞİLDİR (tarayıcı JS'i okuyabilir), değeri
 * `<random>.<hmac>` biçimindedir — HMAC, bu token'ın sunucu tarafından
 * üretildiğini doğrular (BFF_COOKIE_SECRET ile). Double-submit karşılaştırması
 * (cookie === header) ana korumadır; HMAC ek bir bütünlük katmanıdır.
 */
export function generateCsrfToken() {
  const raw = crypto.randomBytes(32).toString("hex");
  const signature = sign(raw);
  return `${raw}.${signature}`;
}

function sign(raw) {
  return crypto.createHmac("sha256", BFF_COOKIE_SECRET).update(raw).digest("hex");
}

function isValidToken(token) {
  if (!token || typeof token !== "string" || !token.includes(".")) return false;
  const [raw, signature] = token.split(".");
  if (!raw || !signature) return false;
  const expected = sign(raw);
  try {
    return crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected));
  } catch {
    return false;
  }
}

/**
 * GET/HEAD/OPTIONS dışındaki tüm BFF route handler'larında çağrılır.
 * @param {Request} request
 * @param {string | undefined} cookieToken ssp_csrf cookie değeri
 * @returns {boolean} true ise istek geçerli
 */
export function isCsrfValid(request, cookieToken) {
  if (["GET", "HEAD", "OPTIONS"].includes(request.method)) return true;
  const headerToken = request.headers.get(CSRF_HEADER_NAME);
  if (!headerToken || !cookieToken) return false;
  if (headerToken !== cookieToken) return false;
  return isValidToken(cookieToken) && isValidToken(headerToken);
}

export { CSRF_HEADER_NAME };
