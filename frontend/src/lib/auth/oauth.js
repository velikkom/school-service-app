import crypto from "node:crypto";
import { BFF_COOKIE_SECRET, OAUTH_REDIRECT_BASE_URL } from "@/lib/auth/env";

export const OAUTH_STATE_COOKIE = "ssp_oauth_state";
export const OAUTH_PENDING_LINK_COOKIE = "ssp_oauth_pending_link";
const STATE_TTL_SECONDS = 10 * 60;
const PENDING_LINK_TTL_SECONDS = 10 * 60;

function base64url(buffer) {
  return buffer.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function sign(payload) {
  return crypto.createHmac("sha256", BFF_COOKIE_SECRET).update(payload).digest("hex");
}

export function redirectUri(providerId) {
  return `${OAUTH_REDIRECT_BASE_URL}/api/auth/social/${providerId}/callback`;
}

/** PKCE code_verifier (43-128 karakter, RFC 7636) + ondan türetilen S256 code_challenge. */
export function generatePkcePair() {
  const codeVerifier = base64url(crypto.randomBytes(48));
  const codeChallenge = base64url(crypto.createHash("sha256").update(codeVerifier).digest());
  return { codeVerifier, codeChallenge };
}

export function generateState() {
  return base64url(crypto.randomBytes(24));
}

/**
 * `state`+`codeVerifier`+`provider`'ı imzalı, HttpOnly, KISA ÖMÜRLÜ (10 dk) bir çerezde taşır —
 * callback'te bu çerez okunup imza+eşleşme doğrulanır, sonra HEMEN silinir (tek kullanımlık).
 */
export function packOAuthState({ provider, state, codeVerifier, from }) {
  const payload = JSON.stringify({
    provider,
    state,
    codeVerifier,
    from,
    exp: Date.now() + STATE_TTL_SECONDS * 1000,
  });
  const encoded = base64url(Buffer.from(payload, "utf8"));
  return `${encoded}.${sign(encoded)}`;
}

export function unpackOAuthState(cookieValue) {
  if (!cookieValue || !cookieValue.includes(".")) return null;
  const [encoded, signature] = cookieValue.split(".");
  if (!encoded || !signature) return null;
  const expected = sign(encoded);
  try {
    if (!crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) return null;
  } catch {
    return null;
  }
  try {
    const payload = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
    if (!payload.exp || payload.exp < Date.now()) return null;
    return payload;
  } catch {
    return null;
  }
}

/**
 * NEEDS_LINKING senaryosunda (docs/SECURITY.md adım 5): doğrulanmış sosyal kimliği, kullanıcı
 * mevcut şifresiyle giriş yapana kadar HttpOnly+imzalı bir çerezde bekletir. Çerez istemci JS'ine
 * HİÇBİR ZAMAN okunabilir açılmaz — `/api/auth/social-link` BFF route'u bunu sunucu tarafında
 * okuyup backend isteğini KENDİSİ kurar, istemci sadece boş bir POST atar.
 */
export function packPendingLink({ provider, providerUserId, email }) {
  const payload = JSON.stringify({
    provider,
    providerUserId,
    email,
    exp: Date.now() + PENDING_LINK_TTL_SECONDS * 1000,
  });
  const encoded = base64url(Buffer.from(payload, "utf8"));
  return `${encoded}.${sign(encoded)}`;
}

export function unpackPendingLink(cookieValue) {
  return unpackOAuthState(cookieValue); // aynı imzalama/çözme mekanizması, farklı payload şekli
}

export function buildAuthUrl(config, providerId, { state, codeChallenge }) {
  const url = new URL(config.authUrl);
  url.searchParams.set("client_id", config.clientId);
  url.searchParams.set("redirect_uri", redirectUri(providerId));
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", config.scope);
  url.searchParams.set("state", state);
  if (config.usePkce) {
    url.searchParams.set("code_challenge", codeChallenge);
    url.searchParams.set("code_challenge_method", "S256");
  }
  return url.toString();
}

export async function exchangeCodeForToken(config, providerId, { code, codeVerifier }) {
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: redirectUri(providerId),
    client_id: config.clientId,
    client_secret: config.clientSecret,
  });
  if (config.usePkce) body.set("code_verifier", codeVerifier);

  const res = await fetch(config.tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
    body: body.toString(),
  });
  if (!res.ok) return null;
  const json = await res.json().catch(() => null);
  if (!json?.access_token) return null;
  return json.access_token;
}

export async function fetchUserInfo(config, providerAccessToken) {
  const res = await fetch(config.userInfoUrl, {
    headers: { Authorization: `Bearer ${providerAccessToken}` },
  });
  if (!res.ok) return null;
  const json = await res.json().catch(() => null);
  if (!json) return null;
  return config.mapUserInfo(json);
}
