"use client";

import { useState } from "react";
import { Button } from "primereact/button";
import StatusBadge, { NoticeBadge } from "@/components/ui/StatusBadge";
import Notice, { Avatar } from "@/components/ui/Notice";
import ReasonDialog from "@/components/feedback/ReasonDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { formatClock, formatTime } from "@/lib/formatting/date";
import {
  attendanceActions,
  canRequestCorrection,
  groupRosterByStop,
  nextStopId,
} from "@/features/trips/lib/fieldUtils";
import { useRecordAttendance } from "@/features/trips/action/useTripActions";
import CorrectionRequestDialog from "./CorrectionRequestDialog";
import styles from "@/features/trips/style/trips.module.scss";

/** Durak başlığı: sıra dairesi, ad, planlı saat; sıradaki durak sarı + rozet; listede yapışkan. */
export function StopHeader({ group, isNext, plain = false }) {
  return (
    <div className={`${styles.stopHead} ${plain ? styles.stopHeadPlain : ""}`}>
      <span className={`${styles.seq} ${isNext ? styles.seqNext : ""}`} aria-hidden="true">
        {group.sequenceNo}
      </span>
      <b>{group.stopName}</b>
      {isNext && <StatusBadge tone="warn" icon="pi-map-marker" label="Sıradaki durak" />}
      <span className={styles.stopTime}>{formatTime(group.plannedTime)}</span>
    </div>
  );
}

/** Yolcu satırı. Sol kenar durumu kodlar; veli bildirimi mor + not kutusu (resmi yoklama değildir). */
export function RosterRow({ row, tripStatus, mode, onAction, onAbsence, onCorrection, busy }) {
  const actions = attendanceActions(row, tripStatus);
  const showCorrection = mode === "field" && canRequestCorrection(row);
  return (
    <div
      className={`${styles.pax} ${styles[`st-${row.status}`] ?? ""} ${row.absenceNotice ? styles.hasNotice : ""} ${mode === "admin" ? styles.compact : ""}`}
      data-testid={`roster-row-${row.studentId}`}
    >
      <div className={styles.who}>
        <Avatar name={row.studentFullName} size={40} />
        <div className={styles.grow}>
          <b>{row.studentFullName}</b>
          <div className="row" style={{ marginTop: 4 }}>
            <StatusBadge group="attendance" value={row.status} />
            {row.absenceNotice && <NoticeBadge />}
            {row.pendingCorrectionId && <StatusBadge tone="warn" icon="pi-history" label="Düzeltme bekliyor" />}
          </div>
        </div>
      </div>
      {row.absenceNotice && (
        <div className={styles.paxNote}>
          <b>
            {row.absenceNotice.parentFullName || "Veli"} · {formatClock(row.absenceNotice.createdAt)}
          </b>{" "}
          — {row.absenceNotice.note || "Not yok"}
          <div className="text-xs muted">Resmi yoklama değildir. Öğrenci durağa gelirse “Bindi” kaydedilebilir.</div>
        </div>
      )}
      {(actions.length > 0 || showCorrection) && (
        <div className={styles.acts}>
          {actions.includes("BOARDING") && (
            <Button type="button" icon="pi pi-arrow-up" label="Bindi" disabled={busy} onClick={() => onAction("BOARDING", row)} />
          )}
          {actions.includes("ABSENCE") && (
            <Button type="button" icon="pi pi-ban" label="Gelmedi" severity="danger" outlined disabled={busy} onClick={() => onAbsence(row)} />
          )}
          {actions.includes("DROPOFF") && (
            <Button type="button" icon="pi pi-arrow-down" label="İndi" className="p-button-secondary" disabled={busy} onClick={() => onAction("DROPOFF", row)} />
          )}
          {showCorrection && (
            <Button type="button" icon="pi pi-history" label="Düzeltme iste" text onClick={() => onCorrection(row)} />
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Durak gruplu yolcu listesi. mode: "field" (48 px butonlar + düzeltme talebi) | "admin" (kompakt).
 * Yoklama butonları yalnız yoldaki (IN_PROGRESS) seferde gösterilir (API ref F-9).
 */
export default function RosterList({ tripId, tripStatus, rows, mode = "field" }) {
  const [absenceRow, setAbsenceRow] = useState(null);
  const [correctionRow, setCorrectionRow] = useState(null);
  const record = useRecordAttendance(tripId);

  if (!rows?.length) {
    return (
      <ScreenState
        kind="empty"
        icon="pi-users"
        title="Bu sefere atanmış öğrenci yok"
        message="Güzergâh ve yön için aktif öğrenci ataması bulunamadı."
        bare={mode === "admin"}
      />
    );
  }

  const groups = groupRosterByStop(rows);
  const next = tripStatus === "IN_PROGRESS" ? nextStopId(groups) : null;
  const pendingStudent = record.isPending ? record.variables?.studentId : null;

  const onAction = (type, row) => record.mutate({ type, studentId: row.studentId, studentName: row.studentFullName });

  return (
    <div className="stack" style={{ gap: 0 }}>
      {groups.map((g) => (
        <div className={styles.stopGroup} key={g.stopId ?? g.sequenceNo}>
          <StopHeader group={g} isNext={next != null && g.stopId === next} plain={mode === "admin"} />
          {g.rows.map((r) => (
            <RosterRow
              key={r.studentId}
              row={r}
              tripStatus={tripStatus}
              mode={mode}
              busy={pendingStudent === r.studentId}
              onAction={onAction}
              onAbsence={setAbsenceRow}
              onCorrection={setCorrectionRow}
            />
          ))}
        </div>
      ))}

      <ReasonDialog
        visible={Boolean(absenceRow)}
        onHide={() => setAbsenceRow(null)}
        title="Gelmedi olarak işaretle"
        subtitle={absenceRow ? `${absenceRow.studentFullName} · ${absenceRow.stopName}` : undefined}
        icon="pi-ban"
        tone="danger"
        label="Not (isteğe bağlı)"
        placeholder="Örn. Durakta 2 dk beklendi"
        required={false}
        maxLength={255}
        confirmLabel="Gelmedi kaydet"
        submitting={record.isPending}
        onConfirm={async (reason) => {
          try {
            await record.mutateAsync({
              type: "ABSENCE",
              studentId: absenceRow.studentId,
              studentName: absenceRow.studentFullName,
              reason,
            });
            setAbsenceRow(null);
          } catch {
            // Hata toast'ı mutasyonda gösterildi; dialog açık kalır.
          }
        }}
      >
        {absenceRow?.absenceNotice && (
          <Notice tone="notice" title="Veli bildirimi var">
            {absenceRow.absenceNotice.parentFullName ? `${absenceRow.absenceNotice.parentFullName}: ` : ""}
            {absenceRow.absenceNotice.note || "Not eklenmemiş."}
          </Notice>
        )}
      </ReasonDialog>

      {mode === "field" && (
        <CorrectionRequestDialog
          key={correctionRow?.studentId ?? "none"}
          tripId={tripId}
          row={correctionRow}
          onHide={() => setCorrectionRow(null)}
        />
      )}
    </div>
  );
}
