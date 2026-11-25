"use client";

import Link from "next/link";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import { MetricCard } from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import { usePendingCounts } from "../hooks/usePendingCounts";
import styles from "../style/requests.module.scss";

const QUEUES = [
  { key: "corrections", href: "/yoklama-duzeltmeleri", label: "Yoklama düzeltmeleri", icon: "pi-history", hint: "Personel düzeltmeleri ve veli inceleme talepleri" },
  { key: "stopChanges", href: "/veli-talepleri", label: "Durak değişiklikleri", icon: "pi-map-marker", hint: "Veli talepleri, yürürlük tarihli" },
  { key: "enrollments", href: "/ogrenci-kayit-talepleri", label: "Öğrenci başvuruları", icon: "pi-id-card", hint: "İlk öz-kayıt ve ek çocuk başvuruları" },
  { key: "registrations", href: "/kayit-talepleri", label: "Hesap kayıt talepleri", icon: "pi-envelope", hint: "E-postası doğrulanmış talepler" },
];

/** ADMIN "Talep Merkezi" (/talepler): dört onay kuyruğunun sayaçlı girişi. */
export default function RequestCenterScreen() {
  const pending = usePendingCounts();
  const counts = pending.data;
  const failed = counts ? QUEUES.filter((q) => counts[q.key] == null) : [];

  return (
    <>
      <PageMeta title="Talep Merkezi" subtitle={counts ? `${counts.all} karar bekliyor` : undefined} />
      <div className="stack-lg">
        {pending.isError && !counts ? (
          <ScreenState kind="error" error={pending.error} onRetry={pending.refetch} />
        ) : (
          <div className={styles.centerGrid}>
            {QUEUES.map((q) => {
              const n = counts?.[q.key];
              return (
                <MetricCard
                  key={q.key}
                  href={q.href}
                  icon={q.icon}
                  label={q.label}
                  value={pending.isPending ? "…" : n == null ? "—" : n}
                  hint={n == null && counts ? "Sayı alınamadı — listeyi açın" : q.hint}
                  attention={n > 0}
                />
              );
            })}
          </div>
        )}
        {failed.length > 0 && (
          <Notice tone="warn" title="Bazı sayaçlar alınamadı">
            {failed.map((q) => q.label).join(", ")} için sayı okunamadı. Kuyruğu açarak kontrol edin.
          </Notice>
        )}
        <Notice tone="muted">
          Sayaçlar 60 sn&apos;de bir yenilenir. Seferlerden gelen iptal talepleri <Link href="/dashboard">Günlük Operasyon</Link> ekranında
          ve ilgili sefer detayının Olaylar sekmesinde karara bağlanır.
        </Notice>
      </div>
    </>
  );
}
