import { NextResponse } from "next/server";
import { getProviderConfig } from "@/lib/auth/oauthProviders";
import {
  OAUTH_STATE_COOKIE,
  OAUTH_PENDING_LINK_COOKIE,
  unpackOAuthState,
  packPendingLink,
  exchangeCodeForToken,
  fetchUserInfo,
} from "@/lib/auth/oauth";
import { setAuthCookies } from "@/lib/auth/authCookies";
import { BACKEND_INTERNAL_URL, INTERNAL_SERVICE_KEY, IS_PRODUCTION } from "@/lib/auth/env";
import { sanitizeLocalPath } from "@/lib/auth/safeRedirect";

/**
 * GET /api/auth/social/{provider}/callback — docs/SECURITY.md "Sosyal giriş" adım 2-4.
 * Sağlayıcıdan dönen `code`'u doğrulanmış kimliğe çevirir, backend'e `POST /api/auth/social-login`
 * ile server-to-server iletir, üç olası sonuca göre kullanıcıyı yönlendirir. Token hiçbir ARA
 * adımda tarayıcıya JSON olarak gösterilmez.
 */
export async function GET(request, context) {
  const { provider } = (await context?.params) ?? {};
  const requestUrl = new URL(request.url);
  const origin = requestUrl.origin;
  const config = getProviderConfig(provider);

  const fail = (reason) => {
    const response = NextResponse.redirect(new URL(`/login?socialError=${reason}`, origin));
    response.cookies.set({ name: OAUTH_STATE_COOKIE, value: "", path: "/api/auth/social", maxAge: 0 });
    return response;
  };

  if (!config) return fail("unconfigured");

  const code = requestUrl.searchParams.get("code");
  const returnedState = requestUrl.searchParams.get("state");
  if (!code || !returnedState) return fail("invalid_response");

  const stateCookie = request.cookies.get(OAUTH_STATE_COOKIE)?.value;
  const parsed = unpackOAuthState(stateCookie);
  if (!parsed || parsed.provider !== provider || parsed.state !== returnedState) {
    return fail("state_mismatch");
  }

  const providerAccessToken = await exchangeCodeForToken(config, provider, {
    code,
    codeVerifier: parsed.codeVerifier,
  });
  if (!providerAccessToken) return fail("token_exchange_failed");

  const identity = await fetchUserInfo(config, providerAccessToken);
  if (!identity?.providerUserId || !identity?.email) return fail("userinfo_failed");

  let backendRes;
  try {
    backendRes = await fetch(`${BACKEND_INTERNAL_URL}/api/auth/social-login`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Internal-Service-Key": INTERNAL_SERVICE_KEY },
      body: JSON.stringify({
        provider: provider.toUpperCase(),
        providerUserId: identity.providerUserId,
        email: identity.email,
        emailVerified: identity.emailVerified,
        fullName: identity.fullName,
      }),
      cache: "no-store",
    });
  } catch {
    return fail("backend_unreachable");
  }

  const data = await backendRes.json().catch(() => null);
  if (!backendRes.ok || !data) return fail("backend_error");

  if (data.status === "AUTHENTICATED") {
    const response = NextResponse.redirect(new URL(sanitizeLocalPath(parsed.from, "/dashboard"), origin));
    response.cookies.set({ name: OAUTH_STATE_COOKIE, value: "", path: "/api/auth/social", maxAge: 0 });
    // Sosyal girişte "Beni hatırla" checkbox'ı yoktur — en kısa/güvenli (oturum çerezi) tavan uygulanır.
    setAuthCookies(
      response,
      { accessToken: data.accessToken, refreshToken: data.refreshToken, refreshTokenExpiresAt: data.refreshTokenExpiresAt },
      { rememberMe: false }
    );
    return response;
  }

  if (data.status === "NEEDS_LINKING") {
    const response = NextResponse.redirect(new URL("/login?socialLink=1", origin));
    response.cookies.set({ name: OAUTH_STATE_COOKIE, value: "", path: "/api/auth/social", maxAge: 0 });
    response.cookies.set({
      name: OAUTH_PENDING_LINK_COOKIE,
      value: packPendingLink({
        provider: provider.toUpperCase(),
        providerUserId: identity.providerUserId,
        email: identity.email,
      }),
      httpOnly: true,
      secure: IS_PRODUCTION,
      sameSite: "lax",
      path: "/api/auth",
      maxAge: 10 * 60,
    });
    return response;
  }

  // PENDING_APPROVAL
  const response = NextResponse.redirect(new URL("/login?socialPending=1", origin));
  response.cookies.set({ name: OAUTH_STATE_COOKIE, value: "", path: "/api/auth/social", maxAge: 0 });
  return response;
}
