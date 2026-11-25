import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import { COOKIE_ACCESS_TOKEN, COOKIE_CSRF_TOKEN } from "@/lib/auth/cookieNames";

/**
 * Kök: oturum varsa rolün ana ekranına (/dashboard yönlendirir), yoksa girişe.
 * Sprint 3 düzeltmesi: access çerezi (15 dk) dolmuş ama oturum sürüyorsa (ssp_csrf refresh çereziyle aynı ömrü
 * paylaşır, bkz. proxy.js) kullanıcı da ana ekrana gider; ilk GET /api/auth/me BFF'te oturumu yeniler. Oturum
 * gerçekten bittiyse istemci girişe yönlendirir. Önceden bu durumda giriş ekranı açılıyordu.
 */
export default async function RootPage() {
  const store = await cookies();
  const hasSession = Boolean(store.get(COOKIE_ACCESS_TOKEN)?.value || store.get(COOKIE_CSRF_TOKEN)?.value);
  redirect(hasSession ? "/dashboard" : "/login");
}
