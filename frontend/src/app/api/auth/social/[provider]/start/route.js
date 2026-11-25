import { NextResponse } from "next/server";
import { getProviderConfig, isProviderConfigured } from "@/lib/auth/oauthProviders";
import { generatePkcePair, generateState, packOAuthState, buildAuthUrl, OAUTH_STATE_COOKIE } from "@/lib/auth/oauth";
import { sanitizeLocalPath } from "@/lib/auth/safeRedirect";
import { IS_PRODUCTION } from "@/lib/auth/env";

/**
 * GET /api/auth/social/{provider}/start — docs/SECURITY.md "Sosyal giriş" adım 1.
 * Sağlayıcı yapılandırılmamışsa (env'de client id/secret yok) login sayfasına açık bir hata
 * koduyla geri döner — ASLA sahte bir yetkilendirme denemesi başlatmaz.
 */
export async function GET(request, context) {
  const { provider } = (await context?.params) ?? {};
  const config = getProviderConfig(provider);
  const requestUrl = new URL(request.url);

  if (!config || !isProviderConfigured(provider)) {
    return NextResponse.redirect(new URL("/login?socialError=unconfigured", requestUrl.origin));
  }

  const state = generateState();
  const { codeVerifier, codeChallenge } = generatePkcePair();
  const from = sanitizeLocalPath(requestUrl.searchParams.get("from"), "/dashboard");

  const response = NextResponse.redirect(buildAuthUrl(config, provider, { state, codeChallenge }));
  response.cookies.set({
    name: OAUTH_STATE_COOKIE,
    value: packOAuthState({ provider, state, codeVerifier, from }),
    httpOnly: true,
    secure: IS_PRODUCTION,
    sameSite: "lax",
    path: "/api/auth/social",
    maxAge: 10 * 60,
  });
  return response;
}
