import { NextResponse } from "next/server";

/**
 * docs/API.md hata response formatı ile uyumlu, BFF'in kendi ürettiği hatalar için
 * (örn. cookie yok, CSRF uyuşmazlığı, allowlist dışı istek, backend'e erişilemiyor).
 * DİKKAT: Windows dosya sistemi büyük/küçük harf duyarsızdır — bu dosya bilerek
 * "lib/clientApiError.js" (tarayıcı taraf hata sınıfı) ile çakışmayacak şekilde adlandırıldı.
 */
export function apiErrorResponse({ status, error, message, path, fieldErrors }) {
  return NextResponse.json(
    {
      timestamp: new Date().toISOString(),
      status,
      error,
      message,
      path,
      ...(fieldErrors ? { fieldErrors } : {}),
    },
    { status }
  );
}

export const unauthorized = (path) =>
  apiErrorResponse({
    status: 401,
    error: "UNAUTHORIZED",
    message: "Oturumunuzun süresi doldu, lütfen tekrar giriş yapın.",
    path,
  });

export const csrfForbidden = (path) =>
  apiErrorResponse({
    status: 403,
    error: "CSRF_TOKEN_INVALID",
    message: "Güvenlik doğrulaması başarısız oldu. Sayfayı yenileyip tekrar deneyin.",
    path,
  });

export const badGateway = (path) =>
  apiErrorResponse({
    status: 502,
    error: "BACKEND_UNAVAILABLE",
    message: "Sunucuya şu anda erişilemiyor. Lütfen daha sonra tekrar deneyin.",
    path,
  });
