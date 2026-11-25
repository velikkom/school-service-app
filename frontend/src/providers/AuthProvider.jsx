"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { authService } from "@/features/auth/service/authService";
import { authKeys } from "@/features/auth/hooks/keys";
import { UNAUTHORIZED_EVENT } from "@/lib/http/client";

const AuthContext = createContext(null);

const PUBLIC_PREFIXES = ["/login", "/kayit-talebi", "/e-posta-dogrula", "/sifremi-unuttum", "/sifre-sifirla"];
const isPublicPath = (p) => PUBLIC_PREFIXES.some((x) => p === x || p.startsWith(`${x}/`));

/**
 * Oturumdaki kullanıcıyı (GET /api/auth/me) paylaşır. Kullanıcı kimliği değiştiğinde (çıkış, başka
 * hesapla giriş) önceki hesabın sorgu önbelleği tamamen temizlenir. Yalnızca UX içindir.
 */
export default function AuthProvider({ children }) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const onPublic = isPublicPath(pathname || "");

  const meQuery = useQuery({
    queryKey: authKeys.me(),
    queryFn: authService.me,
    enabled: !onPublic,
    retry: false,
    staleTime: 5 * 60_000,
  });

  const user = meQuery.data ?? null;
  const lastUserId = useRef(null);

  useEffect(() => {
    const id = user?.id ?? null;
    if (lastUserId.current !== null && id !== null && id !== lastUserId.current) {
      // Farklı hesaba geçildi: önceki hesabın verileri görünmesin.
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== "auth" });
    }
    if (id !== null) lastUserId.current = id;
  }, [user?.id, queryClient]);

  const resetSession = useCallback(() => {
    lastUserId.current = null;
    queryClient.cancelQueries();
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    function onUnauthorized() {
      if (isPublicPath(window.location.pathname)) return;
      resetSession();
      const from = `${window.location.pathname}${window.location.search}`;
      const target = `/login?from=${encodeURIComponent(from)}&expired=1`;
      router.replace(target);
      // Sprint 3 düzeltmesi: oturum ilk sayfa yüklemesinde düşerse (ör. refresh token iptal edilmiş, access
      // çerezi dolmuş) istemci yönlendirmesi uygulanmayabiliyor ve ekran "Oturum bilgisi yükleniyor"da
      // kalıyordu. Kısa süre sonra hâlâ korumalı sayfadaysak tam sayfa yönlendirme yapılır.
      setTimeout(() => {
        if (!isPublicPath(window.location.pathname)) window.location.replace(target);
      }, 1500);
    }
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [router, resetSession]);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } catch {
      // Çerezler BFF'te her durumda temizlenir.
    }
    resetSession();
    router.replace("/login");
    router.refresh();
  }, [resetSession, router]);

  const value = useMemo(
    () => ({
      user,
      role: user?.role ?? null,
      isLoading: meQuery.isLoading,
      isError: meQuery.isError,
      error: meQuery.error,
      refetch: meQuery.refetch,
      logout,
      resetSession,
    }),
    [user, meQuery.isLoading, meQuery.isError, meQuery.error, meQuery.refetch, logout, resetSession]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth, AuthProvider içinde kullanılmalıdır.");
  return ctx;
}
