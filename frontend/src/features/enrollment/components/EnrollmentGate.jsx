"use client";

import { Button } from "primereact/button";
import ScreenState from "@/components/feedback/ScreenState";
import { useAuth } from "@/providers/AuthProvider";
import { useEnrollmentState } from "@/features/enrollment/hooks/useEnrollmentState";
import EnrollmentWizard from "./EnrollmentWizard";
import PendingApprovalScreen from "./PendingApprovalScreen";
import styles from "../style/enrollment.module.scss";

/**
 * PARENT öz-kayıt kapısı. PARENT değilse doğrudan children.
 * WIZARD → kabuk dışında tam ekran sihirbaz; PENDING → "Onay bekliyor"; OPEN → children.
 */
export default function EnrollmentGate({ user, children }) {
  const isParent = user?.role === "PARENT";
  const { logout } = useAuth();
  const query = useEnrollmentState(user?.id, { enabled: isParent });

  if (!isParent) return children;

  if (query.isPending) {
    return (
      <div className={styles.fullscreen}>
        <ScreenState kind="loading" title="Kayıt durumu kontrol ediliyor" rows={3} />
      </div>
    );
  }

  if (query.isError && !query.data) {
    return (
      <div className={styles.fullscreen}>
        <ScreenState
          kind="error"
          title="Kayıt durumu alınamadı"
          error={query.error}
          onRetry={query.refetch}
          action={<Button type="button" label="Çıkış yap" icon="pi pi-sign-out" className="p-button-text" onClick={logout} />}
        />
      </div>
    );
  }

  const { state, pendingStudent } = query.data;

  if (state === "PENDING") {
    return <PendingApprovalScreen studentName={pendingStudent?.fullName} onRefresh={() => query.refetch()} refreshing={query.isFetching} onLogout={logout} />;
  }

  if (state === "WIZARD") {
    return (
      <main className={styles.fullscreen}>
        <div className={styles.brandRow}>
          <span className={styles.brand}>ServisFlow</span>
          <Button type="button" label="Çıkış yap" icon="pi pi-sign-out" className="p-button-text" onClick={logout} />
        </div>
        <section className={styles.gateCard}>
          <h1 className={styles.gateTitle}>Öğrenci kaydı</h1>
          <p className={styles.pendingText}>
            Servis hizmetinden yararlanmak için çocuğunuzun bilgilerini girin. Başvurunuz yönetici onayından sonra aktif olur.
          </p>
          <EnrollmentWizard mode="first" onDone={() => query.refetch()} />
        </section>
      </main>
    );
  }

  return children;
}
