"use client";

import { useState } from "react";
import Link from "next/link";
import { InputText } from "primereact/inputtext";
import { SelectButton } from "primereact/selectbutton";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import Card, { MetricCard } from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import { formatClock, formatDate, formatLongDate, todayISO } from "@/lib/formatting/date";
import { labelOf } from "@/lib/formatting/labels";
import { usePendingCounts } from "@/features/requests/hooks/usePendingCounts";
import OperationTripCard from "./OperationTripCard";
import { PER_TRIP_FETCH_LIMIT, useDailySummary, usePerTripDetails, useTripsOfDay } from "../hooks/useDailyOperations";
import styles from "../style/operations.module.scss";

const DIRECTION_FILTERS = [
  { value: "ALL", label: "Tümü" },
  { value: "MORNING", label: "Gidiş" },
  { value: "AFTERNOON", label: "Dönüş" },
];

const QUEUES = [
  { key: "corrections", href: "/yoklama-duzeltmeleri", label: "Yoklama düzeltmeleri", icon: "pi-history" },
  { key: "stopChanges", href: "/veli-talepleri", label: "Durak değişiklikleri", icon: "pi-map-marker" },
  { key: "enrollments", href: "/ogrenci-kayit-talepleri", label: "Öğrenci başvuruları", icon: "pi-id-card" },
  { key: "registrations", href: "/kayit-talepleri", label: "Hesap kayıt talepleri", icon: "pi-envelope" },
];

/** ADMIN "Günlük Operasyon" (SCREEN_MATRIX §2): metrikler, seferler, karar bekleyenler, olaylar. */
export default function DailyOperationsScreen() {
  const today = todayISO();
  const [date, setDate] = useState(today);
  const [direction, setDirection] = useState("ALL");
  const live = date === today;

  const summary = useDailySummary(date, { live });
  const tripsQuery = useTripsOfDay(date, { live });
  const trips = tripsQuery.data?.trips ?? [];
  const details = usePerTripDetails(trips, { live });
  const pending = usePendingCounts();

  const visibleTrips = direction === "ALL" ? trips : trips.filter((t) => t.direction === direction);
  const tripById = Object.fromEntries(trips.map((t) => [t.id, t]));
  const allIncidents = Object.values(details.incidentsByTrip)
    .flatMap((list) => (Array.isArray(list) ? list : []))
    .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
  const openCancels = allIncidents.filter((i) => i.type === "CANCELLATION_REQUEST" && i.status === "OPEN");
  const events = allIncidents.filter((i) => i.type !== "CANCELLATION_REQUEST");
  const tooMany = tripsQuery.data && !details.enabled && trips.length > 0;
  const truncated = (tripsQuery.data?.totalElements ?? 0) > trips.length;

  const s = summary.data;
  const p = pending.data;

  return (
    <>
      <PageMeta title="Günlük Operasyon" subtitle={formatLongDate(date)} />
      <div className="stack-lg">
        <div className={styles.toolbar}>
          <div className={styles.dateField}>
            <label htmlFor="ops-date">Tarih</label>
            <InputText id="ops-date" type="date" value={date} onChange={(e) => setDate(e.target.value || today)} />
          </div>
          {!live && (
            <button type="button" className="p-button p-button-secondary" onClick={() => setDate(today)}>
              <i className="pi pi-calendar" aria-hidden="true" /> Bugüne dön
            </button>
          )}
        </div>

        {summary.isError && !s ? (
          <ScreenState kind="error" error={summary.error} onRetry={summary.refetch} compact />
        ) : (
          <div className="grid-4">
            <MetricCard
              label="Seferler"
              icon="pi-car"
              value={s ? s.totalTrips : summary.isPending ? "…" : "—"}
              hint={s ? `${s.inProgressCount} yolda · ${s.plannedCount} planlı · ${s.completedCount} tamamlandı · ${s.cancelledCount} iptal` : "Günlük özet"}
              href="/seferler"
            />
            <MetricCard
              label="Yoklama kayıtları"
              icon="pi-check-circle"
              value={s ? `${s.boardingCount} / ${s.absenceCount}` : summary.isPending ? "…" : "—"}
              hint={s ? `Biniş / devamsızlık · ${s.dropoffCount} iniş` : "Biniş / devamsızlık"}
              href="/raporlar"
            />
            <MetricCard
              label="Olaylar"
              icon="pi-exclamation-triangle"
              value={details.enabled ? events.length + openCancels.length : "—"}
              hint={details.enabled ? `${openCancels.length} iptal talebi karar bekliyor` : "Sefer başına izlenir"}
              attention={openCancels.length > 0}
            />
            <MetricCard
              label="Bekleyen talepler"
              icon="pi-inbox"
              value={p ? p.all : pending.isPending ? "…" : "—"}
              hint={p ? `${p.corrections ?? "—"} yoklama · ${p.stopChanges ?? "—"} durak · ${p.enrollments ?? "—"} başvuru · ${p.registrations ?? "—"} hesap` : "Dört onay kuyruğu"}
              href="/talepler"
              attention={(p?.all ?? 0) > 0}
            />
          </div>
        )}

        <div className="grid-main">
          <Card
            title={live ? "Bugünkü seferler" : `${formatDate(date)} seferleri`}
            subtitle="Saat sırasıyla"
            actions={
              <>
                <SelectButton
                  value={direction}
                  options={DIRECTION_FILTERS}
                  onChange={(e) => e.value && setDirection(e.value)}
                  allowEmpty={false}
                  aria-label="Yön filtresi"
                />
                <Link href="/seferler?yeni=1" className="p-button p-button-sm">
                  <i className="pi pi-plus" aria-hidden="true" /> Sefer oluştur
                </Link>
              </>
            }
          >
            {tripsQuery.isPending && !tripsQuery.data ? (
              <ScreenState kind="loading" rows={3} bare />
            ) : tripsQuery.isError && !tripsQuery.data ? (
              <ScreenState kind="error" error={tripsQuery.error} onRetry={tripsQuery.refetch} bare compact />
            ) : visibleTrips.length === 0 ? (
              <ScreenState
                kind="empty"
                icon="pi-car"
                title={trips.length === 0 ? "Bu tarihte sefer yok" : "Bu yönde sefer yok"}
                message={trips.length === 0 ? "Seçili tarihe sefer oluşturulmamış." : "Yön filtresini değiştirin."}
                bare
                compact
              />
            ) : (
              <div className="stack">
                {tooMany && (
                  <Notice tone="muted" title="Yoklama sayaçları gösterilmiyor">
                    Bu tarihte {trips.length} sefer var; kart başına yoklama ve olay isteği yalnızca {PER_TRIP_FETCH_LIMIT} sefere kadar atılır.
                    Ayrıntı için sefer detayını açın.
                  </Notice>
                )}
                {truncated && (
                  <Notice tone="muted" title="İlk 100 sefer gösteriliyor">
                    Tamamı için <Link href="/seferler">Seferler</Link> listesini kullanın.
                  </Notice>
                )}
                <div className={styles.tripGrid}>
                  {visibleTrips.map((t) => (
                    <OperationTripCard
                      key={t.id}
                      trip={t}
                      roster={details.rosterByTrip[t.id]}
                      incidents={details.incidentsByTrip[t.id]}
                      showCounters={details.enabled}
                    />
                  ))}
                </div>
              </div>
            )}
          </Card>

          <div className="stack-lg">
            <Card title="Karar bekleyen" padded={false}>
              <div className={styles.list}>
                {openCancels.map((i) => {
                  const t = tripById[i.tripId];
                  return (
                    <Link key={`c-${i.id}`} href={`/seferler/${i.tripId}?tab=olaylar`} className={styles.li}>
                      <span className={`${styles.liIcon} ${styles.liIconDanger}`} aria-hidden="true">
                        <i className="pi pi-ban" />
                      </span>
                      <span className={styles.liBody}>
                        <span className={styles.liTitle}>İptal talebi · {t?.routeName ?? `Sefer #${i.tripId}`}</span>
                        <span className={styles.liMeta}>
                          {i.reportedByFullName || "Personel"} · {formatClock(i.createdAt)}
                        </span>
                      </span>
                      <i className="pi pi-chevron-right muted" aria-hidden="true" />
                    </Link>
                  );
                })}
                {QUEUES.map((q) => {
                  const n = p?.[q.key];
                  return (
                    <Link key={q.key} href={q.href} className={styles.li}>
                      <span className={styles.liIcon} aria-hidden="true">
                        <i className={`pi ${q.icon}`} />
                      </span>
                      <span className={styles.liBody}>
                        <span className={styles.liTitle}>{q.label}</span>
                        <span className={styles.liMeta}>
                          {pending.isPending ? "Yükleniyor…" : n == null ? "Sayı alınamadı" : n > 0 ? `${n} bekliyor` : "Bekleyen yok"}
                        </span>
                      </span>
                      {n > 0 && <StatusBadge tone="warn" icon="pi-clock" label={String(n)} />}
                      <i className="pi pi-chevron-right muted" aria-hidden="true" />
                    </Link>
                  );
                })}
              </div>
            </Card>

            <Card
              title={live ? "Bugünkü olaylar" : "Olaylar"}
              padded={false}
              footer={<span className={styles.note}>Olaylar sefer başına toplanır; kurum geneli tek bir olay listesi API&apos;de yok.</span>}
            >
              {!details.enabled ? (
                <ScreenState
                  kind="empty"
                  icon="pi-exclamation-triangle"
                  title={trips.length === 0 ? "Sefer yok" : "Olaylar sefer detayında"}
                  message={trips.length === 0 ? "Bu tarihte olay izlenecek sefer bulunmuyor." : "Sefer sayısı fazla olduğu için olaylar burada toplanmadı."}
                  bare
                  compact
                />
              ) : details.incidentsLoading && events.length === 0 ? (
                <ScreenState kind="loading" rows={2} bare />
              ) : events.length === 0 ? (
                <ScreenState
                  kind="empty"
                  icon="pi-check-circle"
                  title="Olay yok"
                  message={details.incidentsError ? "Bazı seferlerin olayları alınamadı." : "Gecikme, arıza veya yol kapanması bildirilmedi."}
                  bare
                  compact
                />
              ) : (
                <div className={styles.list}>
                  {events.map((i) => {
                    const t = tripById[i.tripId];
                    const meta = labelOf("incidentType", i.type);
                    return (
                      <Link key={i.id} href={`/seferler/${i.tripId}?tab=olaylar`} className={styles.li}>
                        <span className={styles.liBody}>
                          <span className="row">
                            <StatusBadge group="incidentType" value={i.type} label={`${meta.label}${i.delayMinutes ? ` +${i.delayMinutes} dk` : ""}`} />
                            <span className="text-xs muted mono">{formatClock(i.createdAt)}</span>
                          </span>
                          <span className={styles.liMeta}>
                            {t?.routeName ?? `Sefer #${i.tripId}`}
                            {i.note ? ` — ${i.note}` : ""}
                          </span>
                        </span>
                      </Link>
                    );
                  })}
                </div>
              )}
            </Card>
          </div>
        </div>
      </div>
    </>
  );
}
