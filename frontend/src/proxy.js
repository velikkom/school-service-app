import { NextResponse } from "next/server";
import { COOKIE_ACCESS_TOKEN, COOKIE_CSRF_TOKEN } from "@/lib/auth/cookieNames";

/**
 * Oturum yönlendirmesi — yalnızca UX (Next.js 16 "proxy", eski middleware). Token geçerliliği ve
 * yetki backend'de doğrulanır. Access çerezi süresi dolmuş ama refresh çerezi olan kullanıcı
 * sayfaya alınır; ilk BFF çağrısı oturumu yeniler.
 */
const AUTH_ENTRY_PATHS = ["/login", "/sifremi-unuttum", "/kayit-talebi"];
// Tek kullanımlık token sayfaları: başka bir oturum açık olsa bile her zaman gösterilir.
const TOKEN_ACTION_PATHS = ["/sifre-sifirla", "/e-posta-dogrula"];
const PUBLIC_PATHS = [...AUTH_ENTRY_PATHS, ...TOKEN_ACTION_PATHS];

const matches = (pathname, list) => list.some((p) => pathname === p || pathname.startsWith(`${p}/`));

export function proxy(request) {
  const { pathname, search } = request.nextUrl;
  if (pathname === "/") return NextResponse.next();

  const hasAccess = Boolean(request.cookies.get(COOKIE_ACCESS_TOKEN)?.value);
  // ssp_rt yalnızca /api/auth yolunda gönderilir, sayfa isteğinde görünmez. ssp_csrf onunla aynı
  // ömrü paylaşır: access çerezi (15 dk) dolmuş ama oturum sürüyorsa sayfa açılır ve ilk
  // GET /api/auth/me çağrısı BFF'te refresh yapar. Oturum gerçekten bittiyse istemci girişe yönlendirir.
  const hasRefreshHint = Boolean(request.cookies.get(COOKIE_CSRF_TOKEN)?.value);
  const hasSession = hasAccess || hasRefreshHint;

  if (!hasSession && !matches(pathname, PUBLIC_PATHS)) {
    const url = new URL("/login", request.url);
    url.searchParams.set("from", `${pathname}${search}`);
    return NextResponse.redirect(url);
  }
  if (hasAccess && matches(pathname, AUTH_ENTRY_PATHS)) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\.(?:png|jpg|jpeg|svg|ico|webp)$).*)"],
};
