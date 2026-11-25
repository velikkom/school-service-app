"use client";

import Link from "next/link";
import StatusBadge from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import ScreenState from "@/components/feedback/ScreenState";
import { labelOf, ROLE_LABELS } from "@/lib/formatting/labels";
import { formatClock, formatRelativeDateTime, formatTime } from "@/lib/formatting/date";
import { stopProgress } from "@/features/trips/lib/fieldUtils";
import styles from "@/features/trips/style/trips.module.scss";

/** Yön rozeti (Gidiş / Dönüş) + isteğe bağlı saat. */
export function DirectionBadge({ direction, time, long = false }) {
  const d = labelOf("direction", direction);
  const text = long ? d.label : d.short ?? d.label;
  return <StatusBadge tone="muted" icon={d.icon} label={time ? `${text} ${time}` : text} />;
}

/** Sefer kartı (DESIGN_SYSTEM §6). Yoldaki sefer sarı sol şeritli. */
export function TripCard({ trip, href, extra }) {
  const active = trip.status === "IN_PROGRESS";
  return (
    <Link href={href} className={`${styles.tripCard} ${active ? styles.tripCardActive : ""}`}>
      <div className="between">
        <span className="row">
          <span className={styles.time}>{formatTime(trip.plannedStartTime) === "—" ? "--:--" : formatTime(trip.plannedStartTime)}</span>
          <DirectionBadge direction={trip.direction} />
        </span>
        <StatusBadge group="tripStatus" value={trip.status} />
      </div>
      <div className={styles.route}>{trip.routeName}</div>
      <div className={styles.crew}>
        <VehiclePlate plate={trip.vehiclePlate} />
        <span>
          <i className="pi pi-user" aria-hidden="true" />
          {trip.driverFullName || "Şoför atanmadı"}
        </span>
        <span>
          <i className="pi pi-users" aria-hidden="true" />
          {trip.hostessFullName || "Hostes yok"}
        </span>
      </div>
      {(trip.openCancellationRequestId || extra) && (
        <div className="row">
          {trip.openCancellationRequestId && <StatusBadge tone="danger" icon="pi-ban" label="İptal talebi var" />}
          {extra}
        </div>
      )}
      {trip.status === "CANCELLED" && trip.cancellationReason && <div className={styles.meta}>Gerekçe: {trip.cancellationReason}</div>}
    </Link>
  );
}

/** Aktif sefer şeridi: koyu kart, 4 sayaç, tam genişlik sarı aksiyon. */
export function ActiveTripStrip({ trip, counts }) {
  const inProgress = trip.status === "IN_PROGRESS";
  const d = labelOf("direction", trip.direction);
  return (
    <section className={styles.strip} aria-label="Aktif sefer">
      <div className="between">
        <span className={styles.stripKicker}>
          {inProgress ? `Aktif sefer · başladı ${formatClock(trip.startedAt)}` : "Sıradaki sefer"}
        </span>
        <VehiclePlate plate={trip.vehiclePlate} inverse />
      </div>
      <div className={styles.stripTitle}>{trip.routeName}</div>
      <div className={styles.stripSub}>
        <i className={`pi ${d.icon}`} aria-hidden="true" />
        {d.label} · planlanan {formatTime(trip.plannedStartTime)}
      </div>
      {counts && (
        <div className={styles.stats}>
          <div>
            <b>{counts.BOARDED}</b>
            <span>Bindi</span>
          </div>
          <div>
            <b>{counts.DROPPED_OFF}</b>
            <span>İndi</span>
          </div>
          <div>
            <b>{counts.ABSENT}</b>
            <span>Gelmedi</span>
          </div>
          <div>
            <b>{counts.NONE}</b>
            <span>Bekleyen</span>
          </div>
        </div>
      )}
      <Link href={`/seferler/${trip.id}?tab=yoklama`} className={styles.stripAction}>
        <i className="pi pi-check-circle" aria-hidden="true" />
        {inProgress ? "Yoklamaya devam et" : "Sefer ekranını aç"}
      </Link>
    </section>
  );
}

/** Alt çizgili, sayaçlı sekme çubuğu (`?tab=` ile adreslenir). */
export function TabBar({ tabs, current, onChange, label = "Sefer sekmeleri" }) {
  return (
    <div className={styles.tabs} role="tablist" aria-label={label}>
      {tabs.map((t) => (
        <button
          key={t.key}
          type="button"
          role="tab"
          aria-selected={current === t.key}
          className={`${styles.tab} ${current === t.key ? styles.tabOn : ""}`}
          onClick={() => onChange(t.key)}
        >
          {t.label}
          {t.count != null && <span className={styles.tabCount}>{t.count}</span>}
        </button>
      ))}
    </div>
  );
}

/** Sıralı durak zaman çizelgesi; yoklamadan türetilen geçildi/sıradaki işaretleri. */
export function StopTimeline({ stops, groups, tripStatus, showCounts = true }) {
  const items = stopProgress(stops, groups, tripStatus);
  if (!items.length) {
    return <ScreenState kind="empty" icon="pi-map-marker" title="Durak tanımlı değil" message="Bu güzergâhta henüz durak yok." bare compact />;
  }
  return (
    <ol className={styles.stops}>
      {items.map((s) => (
        <li
          key={s.id}
          className={`${styles.stopItem} ${s.state === "done" ? styles.stopDone : ""} ${s.state === "current" ? styles.stopCurrent : ""}`}
        >
          <span className={styles.stopClock}>{formatTime(s.plannedTime)}</span>
          <span className={styles.pin} aria-hidden="true" />
          <span className="stack" style={{ gap: 2 }}>
            <span className={styles.stopName}>
              {s.sequenceNo}. {s.name}
            </span>
            {s.state === "current" && (
              <span>
                <StatusBadge tone="warn" icon="pi-map-marker" label="Sıradaki durak" />
              </span>
            )}
            {s.state === "done" && <span className="sr-only">Geçildi</span>}
          </span>
          {showCounts && groups && <span className={styles.meta}>{s.count} öğrenci</span>}
        </li>
      ))}
    </ol>
  );
}

/** Operasyon kayıtları listesi. `renderActions(incident)` ADMIN karar butonları için. */
export function IncidentList({ incidents, renderActions }) {
  if (!incidents?.length) {
    return (
      <ScreenState
        kind="empty"
        icon="pi-check-circle"
        title="Kayıt yok"
        message="Bu sefer için gecikme, arıza veya talep bildirilmedi."
        bare
        compact
      />
    );
  }
  return (
    <div className={styles.list}>
      {incidents.map((i) => {
        const type = labelOf("incidentType", i.type);
        return (
          <div className={styles.li} key={i.id}>
            <span className={styles.liIcon} aria-hidden="true">
              <i className={`pi ${type.icon}`} />
            </span>
            <div className={`${styles.grow} stack`}>
              <div className="row">
                <StatusBadge group="incidentType" value={i.type} suffix={i.delayMinutes ? `+${i.delayMinutes} dk` : undefined} />
                <StatusBadge group="incidentStatus" value={i.status} />
              </div>
              <div className={styles.quote}>{i.note}</div>
              <div className={styles.meta}>
                {i.reportedByFullName}
                {i.reportedByRole ? ` (${ROLE_LABELS[i.reportedByRole] ?? "—"})` : ""} · {formatRelativeDateTime(i.createdAt)}
              </div>
              {i.resolutionNote && (
                <div className={styles.quote}>
                  <b>Karar:</b> {i.resolutionNote}
                  {i.resolvedByFullName ? ` — ${i.resolvedByFullName}` : ""} · {formatRelativeDateTime(i.resolvedAt)}
                </div>
              )}
              {renderActions?.(i)}
            </div>
          </div>
        );
      })}
    </div>
  );
}
