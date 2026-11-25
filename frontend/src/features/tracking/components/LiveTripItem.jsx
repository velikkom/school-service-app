"use client";

import StatusBadge from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import { useLatestLocation } from "@/features/tracking/hooks/useLatestLocation";
import { formatAgo } from "@/lib/formatting/date";
import styles from "./LiveLocationScreen.module.scss";

/** Konum tazeliği rozeti: konum yok / bağlantı yok (>90 sn) / canlı (N sn önce). */
export function FreshnessBadge({ location, loading, error }) {
  if (loading) return <StatusBadge tone="muted" icon="pi-spinner" label="Konum…" />;
  if (error) return <StatusBadge tone="danger" icon="pi-exclamation-circle" label="Konum alınamadı" />;
  if (!location) return <StatusBadge tone="warn" icon="pi-map-marker" label="Konum yok" />;
  if (location.isOffline) return <StatusBadge tone="danger" icon="pi-wifi" label="Bağlantı yok" />;
  return <StatusBadge tone="live" label={formatAgo(location.staleSeconds)} />;
}

/** Yoldaki sefer satırı (seçilebilir). Konumu sefer başına 15 sn'de bir yenilenir. */
export default function LiveTripItem({ trip, selected, onSelect }) {
  const loc = useLatestLocation(trip.id);
  return (
    <button
      type="button"
      className={`${styles.item} ${selected ? styles.itemOn : ""}`}
      onClick={() => onSelect(trip.id)}
      aria-pressed={selected}
    >
      <span className={styles.itemBody}>
        <span className={styles.itemTitle}>{trip.routeName || `Sefer #${trip.id}`}</span>
        <span className={styles.itemMeta}>
          <VehiclePlate plate={trip.vehiclePlate} />
          <span>{trip.driverFullName || "Şoför yok"}</span>
        </span>
      </span>
      <FreshnessBadge location={loc.data} loading={loc.isPending} error={loc.isError && loc.data === undefined} />
    </button>
  );
}
