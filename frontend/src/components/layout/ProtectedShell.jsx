"use client";

import { usePathname } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";
import { canAccessPath, homePathFor } from "@/lib/rbac/access";
import ScreenState from "@/components/feedback/ScreenState";
import AppShell from "@/components/layout/AppShell";
import { PageMetaProvider } from "@/components/layout/PageMeta";
import EnrollmentGate from "@/features/enrollment/components/EnrollmentGate";
import styles from "./ProtectedShell.module.scss";

/**
 * Oturum + rol kapısı. /me yüklenirken iskelet, hata/oturum yoksa açıklama; rolün açamayacağı bir
 * route doğrudan yazılırsa "Yetki yok" ekranı. PARENT için öz-kayıt kapısı (EnrollmentGate).
 */
export default function ProtectedShell({ children }) {
  const { user, isLoading, isError, error, refetch } = useAuth();
  const pathname = usePathname() || "/";

  if (isLoading || (!user && !isError)) {
    return (
      <div className={styles.center}>
        <ScreenState kind="loading" title="Oturum bilgisi yükleniyor" rows={3} />
      </div>
    );
  }

  if (isError || !user) {
    return (
      <div className={styles.center}>
        <ScreenState
          kind="error"
          title="Oturum doğrulanamadı"
          message={error?.status === 401 ? "Oturumunuzun süresi doldu. Giriş sayfasına yönlendiriliyorsunuz." : "Oturum bilgisi alınamadı."}
          error={error}
          onRetry={refetch}
        />
      </div>
    );
  }

  const allowed = canAccessPath(user.role, pathname);

  return (
    <PageMetaProvider>
      <EnrollmentGate user={user}>
        <AppShell>
          {allowed ? children : <ScreenState kind="forbidden" homeHref={homePathFor(user.role)} />}
        </AppShell>
      </EnrollmentGate>
    </PageMetaProvider>
  );
}
