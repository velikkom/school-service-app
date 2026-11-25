"use client";

import { Button } from "primereact/button";
import StatusBadge, { NoticeBadge } from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import Notice from "@/components/ui/Notice";
import LocationPanel from "@/components/ui/LocationPanel";
import { useLatestLocation } from "@/features/tracking/hooks/useLatestLocation";
import { labelOf } from "@/lib/formatting/labels";
import { formatClock, formatTime } from "@/lib/formatting/date";
import { delayMinutesOf, isOpenTrip, noticeActionsFor } from "@/features/daily-service/hooks/dailyServiceModel";
import styles from "../style/dailyService.module.scss";

function LiveLocation({ tripId }) {
  const q = useLatestLocation(tripId);
  if (q.isPending) return <LocationPanel state="loading" compact />;
  if (q.isError) return <LocationPanel state="error" onRetry={q.refetch} compact />;
  return <LocationPanel location={q.data} tripStatus="IN_PROGRESS" compact />;
}

/**
 * Günlük servis kartı (DailyServiceEntryResponse). `hero` ana kart: büyük saat, durak satırı, konum.
 * Aksiyonlar yalnız PARENT + API bayraklarına göre çizilir.
 */
export default function ServiceCard({ entry, hero = false, role, onSubmitNotice, onWithdrawNotice, busy = false }) {
  const dir = labelOf("direction", entry.direction);
  const delay = delayMinutesOf(entry);
  const { canSubmit, canWithdraw, lockedByAttendance } = noticeActionsFor(entry, role);
  const incident = isOpenTrip(entry) ? entry.latestIncident : null;
  const incidentMeta = incident ? labelOf("incidentType", incident.type) : null;
  const isParent = role === "PARENT";

  const statusRow = (
    <div className={styles.badges}>
      <StatusBadge group="tripStatus" value={entry.status} />
      {delay != null && <StatusBadge tone="warn" icon="pi-clock" label={`Gecikme +${delay} dk`} />}
      {entry.attendanceStatus && entry.attendanceStatus !== "NONE" && <StatusBadge group="attendance" value={entry.attendanceStatus} />}
      {entry.absenceNotice && <NoticeBadge />}
    </div>
  );

  const boxes = (
    <>
      {entry.status === "CANCELLED" && (
        <Notice tone="danger" title="Bu sefer iptal edildi" icon="pi-ban">
          {entry.cancellationReason || "Gerekçe belirtilmedi."}
        </Notice>
      )}
      {incident && (
        <Notice tone="warn" title={`${incidentMeta.label}${incident.delayMinutes ? `: yaklaşık ${incident.delayMinutes} dk` : ""}`} icon={incidentMeta.icon}>
          {incident.note} <span className={styles.meta}>({formatClock(incident.createdAt)})</span>
        </Notice>
      )}
      {entry.absenceNotice && (
        <Notice tone="notice" icon="pi-flag" title={isParent ? "“Servise binmeyecek” bildirdiniz" : "Veliniz “servise binmeyecek” bildirdi"}>
          <span>
            {entry.absenceNotice.note || "Not eklenmedi"} · {formatClock(entry.absenceNotice.createdAt)}
          </span>
          <span className={styles.metaBlock}>Görevliye iletildi. Resmi yoklama değildir.</span>
        </Notice>
      )}
    </>
  );

  const actions =
    canSubmit || canWithdraw || lockedByAttendance ? (
      <div className={styles.actions}>
        {canSubmit && (
          <Button
            type="button"
            icon="pi pi-flag"
            label="Servise binmeyecek"
            className={`p-button-secondary ${styles.tapBtn}`}
            onClick={() => onSubmitNotice?.(entry)}
            disabled={busy}
          />
        )}
        {canWithdraw && (
          <Button
            type="button"
            icon="pi pi-undo"
            label="Bildirimi geri al"
            className={`p-button-text ${styles.tapBtn}`}
            onClick={() => onWithdrawNotice?.(entry)}
            disabled={busy}
          />
        )}
        {lockedByAttendance && <p className={styles.meta}>Yoklama girildiği için bildirim gönderilemez veya geri alınamaz.</p>}
      </div>
    ) : null;

  if (!hero) {
    return (
      <article className={styles.card} aria-label={`${dir.label} servisi`}>
        <div className={styles.between}>
          <span className={styles.row}>
            <StatusBadge tone="muted" icon={dir.icon} label={dir.short} />
            <span className={styles.timeSm}>{formatTime(entry.stopPlannedTime)}</span>
          </span>
          <VehiclePlate plate={entry.vehiclePlate} />
        </div>
        {statusRow}
        <div className={styles.small}>
          <b>{entry.stopName || "Durak bilgisi yok"}</b> durağı · {entry.routeName || "—"}
        </div>
        {boxes}
        {actions}
      </article>
    );
  }

  return (
    <article className={styles.hero} aria-label={`${dir.label} servisi`}>
      <div className={styles.heroTop}>
        <div className={styles.between}>
          <StatusBadge tone="muted" icon={dir.icon} label={dir.label} />
          <VehiclePlate plate={entry.vehiclePlate} />
        </div>
        <div className={styles.when}>
          <span className={styles.timeLg}>{formatTime(entry.stopPlannedTime)}</span>
          <span className={styles.meta}>durakta planlanan saat</span>
        </div>
        <div className={styles.stopline}>
          <i className="pi pi-map-marker" aria-hidden="true" />
          <div className={styles.minw0}>
            <b>{entry.stopName || "Durak bilgisi yok"}</b>
            <div className={styles.small}>
              {entry.routeName || "—"} · servis çıkışı <span className="mono">{formatTime(entry.plannedStartTime)}</span>
            </div>
          </div>
        </div>
        {statusRow}
        {boxes}
      </div>
      {entry.status === "IN_PROGRESS" && <LiveLocation tripId={entry.tripId} />}
      {entry.status === "PLANNED" && <LocationPanel tripStatus="PLANNED" compact />}
      {actions && <div className={styles.heroActions}>{actions}</div>}
    </article>
  );
}
