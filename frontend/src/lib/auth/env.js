// Sunucu tarafı ortam değişkenleri (BFF). Bu dosya sadece route handler/middleware içinde kullanılır.
// Tarayıcıya hiçbir secret sızdırılmaz.

export const BACKEND_INTERNAL_URL =
  process.env.BACKEND_INTERNAL_URL || "http://localhost:8080";

export const JWT_ACCESS_TOKEN_TTL_MINUTES = Number(
  process.env.JWT_ACCESS_TOKEN_TTL_MINUTES || 15
);

export const JWT_REFRESH_TOKEN_TTL_DAYS = Number(
  process.env.JWT_REFRESH_TOKEN_TTL_DAYS || 1
);

// Çift-gönderim CSRF token'ını imzalamak için kullanılan sunucu-taraf secret.
// .env.example'da placeholder olarak tanımlıdır, gerçek değer .env.local'da olmalıdır.
export const BFF_COOKIE_SECRET =
  process.env.BFF_COOKIE_SECRET || "dev-only-insecure-secret-change-me";

export const IS_PRODUCTION = process.env.NODE_ENV === "production";

// Sosyal giriş (OAuth2) callback adreslerinin temel yolu — bkz. @/lib/oauthProviders,
// docs/SECURITY.md "Sosyal giriş". Sağlayıcı konsolunda TANIMLI redirect URI ile eşleşmelidir.
export const OAUTH_REDIRECT_BASE_URL =
  process.env.OAUTH_REDIRECT_BASE_URL || "http://localhost:3000";

// Backend'in SADECE BFF'ten (server-to-server) kabul ettiği POST /api/auth/social-login
// çağrısını imzalayan paylaşılan sır — bkz. docs/SECURITY.md "Sosyal giriş". Backend'deki
// app.security.internal-service-key İLE AYNI olmalıdır; boşsa backend bu uç noktayı HER ZAMAN
// reddeder (fail-closed), bu yüzden burada boş bırakılırsa sosyal giriş callback'i backend_error
// ile başarısız olur.
export const INTERNAL_SERVICE_KEY = process.env.INTERNAL_SERVICE_KEY || "";
