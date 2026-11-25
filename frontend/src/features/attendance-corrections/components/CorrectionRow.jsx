"use client";

import Link from "next/link";
import { Button } from "primereact/button";
import StatusBadge from "@/components/ui/StatusBadge";
import { labelOf, ROLE_LABELS } from "@/lib/formatting/labels";
import { formatDate, formatRelativeDateTime } from "@/lib/formatting/date";
import styles from "@/features/attendance-corrections/style/corrections.module.scss";

/** Talep anındaki → istenen durum (REVIEW'da hedef olmayabilir). */
export function StatusTransition({ correction }) {
  return (
    <div className={styles.line}>
      <StatusBadge group="attendance" value={correction.sourceStatus} />
      <i className={`pi pi-arrow-right ${styles.arrow}`} aria-label="olması gereken" />
      {correction.requestedStatus ? (
        <StatusBadge group="attendance" value={correction.requestedStatus} />
      ) : (
        <StatusBadge tone="muted" icon="pi-search" label="İnceleme (hedef yok)" />
      )}
    </div>
  );
}

/**
 * Talep satırı (RequestQueueRow). `admin` ise bekleyen talepte Reddet/Onayla; `stale=true` ise
 * "Kayıt değişmiş" uyarısı ve onay pasif (onay 409 döner — API ref §10).
 */
export default function CorrectionRow({ correction: c, admin = false, onApprove, onReject }) {
  const pending = c.status === "PENDING";
  const stale = pending && Boolean(c.stale);
  return (
    <div className={styles.row} data-testid={`correction-${c.id}`}>
      <div className={styles.main}>
        <div className={styles.line}>
          <StatusBadge group="requestStatus" value={c.status} />
          <StatusBadge group="correctionKind" value={c.kind} />
          {stale && <StatusBadge tone="danger" icon="pi-exclamation-triangle" label="Kayıt değişmiş" />}
          <span className={styles.meta}>
            #{c.id} · {formatRelativeDateTime(c.createdAt)}
          </span>
        </div>
        <div className={styles.line}>
          <span className={styles.name}>{c.studentFullName}</span>
          <span className={styles.meta}>
            · {formatDate(c.tripBusinessDate)} {labelOf("direction", c.tripDirection).short}
          </span>
          {admin && (
            <Link href={`/seferler/${c.tripId}?tab=yolcular`} className={styles.meta}>
              Sefer #{c.tripId}
            </Link>
          )}
        </div>
        <StatusTransition correction={c} />
        {pending && c.currentStatus && (
          <div className={styles.line}>
            <span className={styles.meta}>Mevcut kayıt:</span>
            <StatusBadge group="attendance" value={c.currentStatus} />
          </div>
        )}
        {c.appliedStatus && (
          <div className={styles.line}>
            <span className={styles.meta}>Uygulanan:</span>
            <StatusBadge group="attendance" value={c.appliedStatus} />
          </div>
        )}
        <div className={styles.quote}>“{c.reason}”</div>
        {admin && (
          <div className={styles.meta}>
            {c.requestedByFullName} ({ROLE_LABELS[c.requestedByRole] ?? "—"})
          </div>
        )}
        {c.decisionNote && (
          <div className={styles.quote}>
            <b>{c.status === "REJECTED" ? "Ret gerekçesi:" : "Karar notu:"}</b> {c.decisionNote}
            {c.decidedAt ? ` · ${formatRelativeDateTime(c.decidedAt)}` : ""}
          </div>
        )}
        {!c.decisionNote && c.decidedAt && <div className={styles.meta}>Karar: {formatRelativeDateTime(c.decidedAt)}</div>}
      </div>
      {admin && pending && (
        <div className={styles.actions}>
          <Button type="button" icon="pi pi-times" label="Reddet" className="p-button-secondary" size="small" onClick={() => onReject(c)} />
          <Button
            type="button"
            icon="pi pi-check"
            label="Onayla"
            size="small"
            disabled={stale}
            aria-describedby={stale ? `stale-${c.id}` : undefined}
            onClick={() => onApprove(c)}
          />
          {stale && (
            <span id={`stale-${c.id}`} className={styles.staleHint}>
              Kayıt talepten sonra değişti; onaylanamaz. Talebi reddedip yeniden açılmasını isteyin.
            </span>
          )}
        </div>
      )}
    </div>
  );
}
