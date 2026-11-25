"use client";

import { Button } from "primereact/button";
import StepBar from "./StepBar";
import styles from "../style/enrollment.module.scss";

/** İlk öz-kayıt sonrası "Onay bekliyor" kapısı (kabuk dışında; menü yok). 15 sn'de bir otomatik kontrol edilir. */
export default function PendingApprovalScreen({ studentName, onRefresh, refreshing, onLogout }) {
  return (
    <main className={styles.fullscreen}>
      <div className={styles.brand}>ServisFlow</div>
      <section className={styles.pendingCard} aria-live="polite">
        <div className={styles.pendingIcon} aria-hidden="true">
          <i className="pi pi-clock" />
        </div>
        <h1 className={styles.pendingTitle}>Başvurunuz yönetici onayında</h1>
        <p className={styles.pendingText}>
          {studentName ? `${studentName} için yaptığınız kayıt alındı.` : "Kaydınız alındı."} Onaylandığında bildirim alacak ve servis
          bilgilerini görebileceksiniz. Bu sürede uygulamanın diğer bölümleri kapalıdır; bu ekran kendini düzenli olarak yeniler.
        </p>
        <StepBar steps={["Başvuru", "Yönetici onayı", "Servis ataması"]} current={1} />
        <div className={styles.pendingActions}>
          <Button type="button" icon="pi pi-refresh" label="Durumu yenile" className="p-button-secondary" onClick={onRefresh} loading={refreshing} />
          <Button type="button" icon="pi pi-sign-out" label="Çıkış yap" className="p-button-text" onClick={onLogout} />
        </div>
      </section>
    </main>
  );
}
