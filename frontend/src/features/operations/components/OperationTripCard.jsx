import Link from "next/link";
import StatusBadge, { NoticeBadge } from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import { formatTime } from "@/lib/formatting/date";
import { labelOf } from "@/lib/formatting/labels";
import { rosterCounts } from "../hooks/useDailyOperations";
import styles from "../style/operations.module.scss";

/**
 * Günlük Operasyon sefer kartı: saat, yön, durum, güzergâh, plaka, şoför/hostes, gecikme ve iptal
 * talebi rozetleri, yoklama sayacı. Yoldaki sefer sarı sol şeritlidir. Kart sefer detayına gider.
 */
export default function OperationTripCard({ trip, roster, incidents, showCounters }) {
  const live = trip.status === "IN_PROGRESS";
  const list = Array.isArray(incidents) ? incidents : [];
  const delay = list.find((i) => i.type === "DELAY");
  const openCancel = list.some((i) => i.type === "CANCELLATION_REQUEST" && i.status === "OPEN");
  const counts = roster ? rosterCounts(roster) : null;

  return (
    <Link href={`/seferler/${trip.id}`} className={`${styles.tripCard} ${live ? styles.tripLive : ""}`}>
      <div className={styles.tripTop}>
        <span className={styles.tripTime}>{formatTime(trip.plannedStartTime)}</span>
        <StatusBadge group="direction" value={trip.direction} label={labelOf("direction", trip.direction).short} />
        <StatusBadge group="tripStatus" value={trip.status} />
      </div>
      <div className={styles.tripRoute}>{trip.routeName || "Güzergâh"}</div>
      <div className={styles.tripMeta}>
        <VehiclePlate plate={trip.vehiclePlate} />
        <span className="text-sm muted">
          {trip.driverFullName || "Şoför yok"} · {trip.hostessFullName || "Hostes yok"}
        </span>
      </div>
      {(delay || openCancel || counts?.notices > 0 || (showCounters && counts)) && (
        <div className={styles.tripFoot}>
          {delay && <StatusBadge tone="warn" icon="pi-clock" label={`Gecikme${delay.delayMinutes ? ` +${delay.delayMinutes} dk` : ""}`} />}
          {openCancel && <StatusBadge tone="danger" icon="pi-ban" label="İptal talebi" />}
          {counts?.notices > 0 && <NoticeBadge label={`${counts.notices} veli bildirimi`} />}
          {showCounters && counts && (
            <span className={styles.counter} aria-label={`Yoklama: ${counts.total} öğrenciden ${counts.recorded} kayıtlı, ${counts.absent} gelmedi`}>
              <i className="pi pi-check-circle" aria-hidden="true" />
              <b className="mono">
                {counts.recorded}/{counts.total}
              </b>
              <span className="muted">yoklama</span>
              {counts.absent > 0 && <span className={styles.counterAbsent}>· {counts.absent} gelmedi</span>}
            </span>
          )}
        </div>
      )}
    </Link>
  );
}
