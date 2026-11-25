// Sosyal giriş sağlayıcı yapılandırması — SADECE sunucu tarafı (bu dosya hiçbir route'tan
// tarayıcıya aktarılmaz). docs/SECURITY.md "Sosyal giriş": client id/secret boşsa sağlayıcı
// "configured" sayılmaz, hiçbir UI/route onu aktifmiş gibi göstermez.
//
// Endpoint URL'leri resmi OAuth2/OIDC uç noktalarıdır (bkz. her sağlayıcının kendi geliştirici
// belgeleri) — GERÇEK bir client id/secret bu ortamda tanımlı olmadığı için bu uç noktalara karşı
// uçtan uca bir akış bu oturumda TEST EDİLEMEDİ (bkz. docs/KNOWN_LIMITATIONS.md Karar Günlüğü,
// 2026-10-23). Kod, standart OAuth2 authorization-code + PKCE akışını uygular.

const RAW_PROVIDERS = {
  google: {
    label: "Google",
    authUrl: "https://accounts.google.com/o/oauth2/v2/auth",
    tokenUrl: "https://oauth2.googleapis.com/token",
    userInfoUrl: "https://openidconnect.googleapis.com/v1/userinfo",
    scope: "openid email profile",
    clientId: process.env.GOOGLE_OAUTH_CLIENT_ID,
    clientSecret: process.env.GOOGLE_OAUTH_CLIENT_SECRET,
    usePkce: true,
    mapUserInfo: (json) => ({
      providerUserId: json.sub,
      email: json.email,
      emailVerified: Boolean(json.email_verified),
      fullName: json.name,
    }),
  },
  facebook: {
    label: "Facebook",
    authUrl: "https://www.facebook.com/v19.0/dialog/oauth",
    tokenUrl: "https://graph.facebook.com/v19.0/oauth/access_token",
    userInfoUrl: "https://graph.facebook.com/me?fields=id,name,email",
    scope: "email public_profile",
    clientId: process.env.FACEBOOK_OAUTH_CLIENT_ID,
    clientSecret: process.env.FACEBOOK_OAUTH_CLIENT_SECRET,
    usePkce: true,
    mapUserInfo: (json) => ({
      providerUserId: json.id,
      email: json.email,
      // Facebook Graph API doğrulanmış e-posta bilgisini ayrı dönmez; sağlayıcı bu bilgiyi
      // vermiyorsa güvenli taraf: doğrulanmamış say (backend kendi doğrulama e-postasını gönderir).
      emailVerified: false,
      fullName: json.name,
    }),
  },
  x: {
    label: "X",
    authUrl: "https://twitter.com/i/oauth2/authorize",
    tokenUrl: "https://api.twitter.com/2/oauth2/token",
    userInfoUrl: "https://api.twitter.com/2/users/me?user.fields=confirmed_email",
    scope: "users.read tweet.read offline.access",
    clientId: process.env.X_OAUTH_CLIENT_ID,
    clientSecret: process.env.X_OAUTH_CLIENT_SECRET,
    usePkce: true,
    // X userinfo e-posta döndürmez (email scope'u yok) — backend email zorunlu kıldığı için
    // (SocialLoginRequest.email @NotBlank) bu sağlayıcı e-posta vermeyen hesaplarda backend'den
    // 400 alır; bu BİLİNEN bir X API kısıtıdır (uygulama eksikliği değil).
    mapUserInfo: (json) => ({
      providerUserId: json.data?.id,
      email: json.data?.confirmed_email,
      emailVerified: Boolean(json.data?.confirmed_email),
      fullName: json.data?.name,
    }),
  },
};

export function getProviderConfig(providerId) {
  return RAW_PROVIDERS[providerId];
}

export function isProviderConfigured(providerId) {
  const cfg = RAW_PROVIDERS[providerId];
  return Boolean(cfg?.clientId && cfg?.clientSecret);
}

export function listConfiguredProviders() {
  return Object.fromEntries(
    Object.keys(RAW_PROVIDERS).map((id) => [id, isProviderConfigured(id)])
  );
}

export function providerIds() {
  return Object.keys(RAW_PROVIDERS);
}
