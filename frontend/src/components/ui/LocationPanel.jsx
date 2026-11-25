"use client";

import StatusBadge from "@/components/ui/StatusBadge";
import { formatAgo, formatClock } from "@/lib/formatting/date";
import styles from "./LocationPanel.module.scss";

/**
 * Son konum paneli (DESIGN_SYSTEM.md §6 "Harita paneli"). Harita sağlayıcısı eklenmedi: projede
 * harita paketi yok ve durak koordinatı API'de bulunmuyor. Panel yalnızca API'nin verdiği son konumu,
 * zamanını ve tazeliğini gösterir; ETA/iz geçmişi gösterilmez. "Haritada aç" kullanıcı tıklarsa
 * OpenStreetMap'te yeni sekmede açılır (sayfaya dış kaynak gömülmez).
 *
 * state: "live" | "notStarted" | "noLocation" | "inactive" | "loading" | "error"
 */
export default function LocationPanel({ location, state, tripStatus, compact = false, onRetry }) {
  let resolved = state;
  if (!resolved) {
    if (tripStatus && tripStatus === "PLANNED") resolved = "notStarted";
    else if (tripStatus && tripStatus !== "IN_PROGRESS" && !location) resolved = "inactive";
    else if (!location) resolved = "noLocation";
    else resolved = "live";
  }

  if (resolved !== "live") {
    const msg = {
      notStarted: ["pi-clock", "Sefer henüz başlamadı", "Sefer başladığında aracın son konumu burada görünür."],
      noLocation: ["pi-map-marker", "Henüz konum bilgisi yok", "Araçtan konum gönderildiğinde son konum ve saati burada görünür."],
      inactive: ["pi-ban", "Sefer aktif değil", "Konum yalnızca yoldaki seferler için gösterilir."],
      loading: ["pi-spin pi-spinner", "Konum yükleniyor", ""],
      error: ["pi-exclamation-circle", "Konum alınamadı", "Bağlantınızı kontrol edip tekrar deneyin."],
    }[resolved];
    return (
      <div className={`${styles.panel} ${styles.empty} ${compact ? styles.compact : ""}`} aria-live="polite">
        <div className={styles.msg}>
          <i className={`pi ${msg[0]}`} aria-hidden="true" />
          <b>{msg[1]}</b>
          {msg[2] && <span>{msg[2]}</span>}
          {resolved === "error" && onRetry && (
            <button type="button" className="p-button p-button-secondary p-button-sm" onClick={onRetry}>
              Tekrar dene
            </button>
          )}
        </div>
      </div>
    );
  }

  const offline = Boolean(location.isOffline);
  const lat = Number(location.lat);
  const lng = Number(location.lng);
  const osm = `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=16/${lat}/${lng}`;

  return (
    <div className={`${styles.panel} ${compact ? styles.compact : ""}`} aria-live="polite">
      <div className={styles.grid} aria-hidden="true" />
      <div className={styles.marker} aria-hidden="true">
        <i className="pi pi-car" />
      </div>
      <div className={styles.overlay}>
        {offline ? (
          <StatusBadge tone="danger" icon="pi-wifi" label="Bağlantı yok" />
        ) : (
          <StatusBadge tone="live" label="Canlı" />
        )}
        <div className={styles.stamp}>
          <b className="mono">Son konum: {formatClock(location.recordedAt)}</b>
          <span>Güncellendi: {formatAgo(location.staleSeconds)}</span>
        </div>
      </div>
      <div className={styles.foot}>
        <span className="mono text-xs">
          {lat.toFixed(5)}, {lng.toFixed(5)}
        </span>
        <a href={osm} target="_blank" rel="noopener noreferrer" className={styles.link}>
          Haritada aç <i className="pi pi-external-link" aria-hidden="true" />
          <span className="sr-only">(yeni sekmede açılır)</span>
        </a>
      </div>
    </div>
  );
}
