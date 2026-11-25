"use client";

import { Suspense, useMemo, useState } from "react";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import StatusBadge from "@/components/ui/StatusBadge";
import Notice from "@/components/ui/Notice";
import Field from "@/components/ui/Field";
import { useAuth } from "@/providers/AuthProvider";
import { addDaysISO, formatClock, formatLongDate, todayISO } from "@/lib/formatting/date";
import ChildSwitcher from "@/components/ui/ChildSwitcher";
import { useSelectedChild } from "@/features/students/hooks/useSelectedChild";
import { useSelfStudent } from "@/features/students/hooks/useMyStudents";
import { useAttendanceHistory, usePendingReviews } from "@/features/attendance/hooks/useAttendanceHistory";
import { groupByTrip } from "@/features/attendance/hooks/attendanceModel";
import ReviewRequestDialog from "./ReviewRequestDialog";
import styles from "../style/attendance.module.scss";

function RangeFilter({ from, to, onChange }) {
  const today = todayISO();
  const [draft, setDraft] = useState({ from, to });
  const invalid = !draft.from || !draft.to || draft.from > draft.to;
  return (
    <form
      className={styles.filter}
      onSubmit={(e) => {
        e.preventDefault();
        if (!invalid) onChange(draft);
      }}
    >
      <Field label="Başlangıç" htmlFor="att-from">
        <InputText id="att-from" type="date" value={draft.from} max={today} onChange={(e) => setDraft((d) => ({ ...d, from: e.target.value }))} />
      </Field>
      <Field label="Bitiş" htmlFor="att-to" error={invalid && draft.from && draft.to ? "Bitiş, başlangıçtan önce olamaz." : undefined}>
        <InputText id="att-to" type="date" value={draft.to} max={today} onChange={(e) => setDraft((d) => ({ ...d, to: e.target.value }))} />
      </Field>
      <Button type="submit" label="Göster" icon="pi pi-filter" className={`p-button-secondary ${styles.tap}`} disabled={invalid} />
    </form>
  );
}

function HistoryList({ student, role, range }) {
  const isParent = role === "PARENT";
  const query = useAttendanceHistory(student.id, range.from, range.to);
  const pending = usePendingReviews({ enabled: isParent });
  const [reviewRow, setReviewRow] = useState(null);

  const pendingTrips = useMemo(
    () => new Set((pending.data ?? []).filter((c) => c.studentId === student.id).map((c) => c.tripId)),
    [pending.data, student.id]
  );

  return (
    <QueryState
      query={query}
      loadingRows={4}
      isEmpty={(d) => !d?.length}
      forbiddenHome="/servisim"
      empty={
        <ScreenState
          kind="empty"
          icon="pi-history"
          title={isParent ? "Yoklama kaydı yok" : "Yoklama kaydın yok"}
          message="Seçilen tarih aralığında kayıt bulunmuyor. Aralığı genişletmeyi deneyin."
        />
      }
    >
      {(events) => {
        const rows = groupByTrip(events);
        return (
          <>
            <ul className={styles.list} aria-label="Yoklama kayıtları">
              {rows.map((row) => {
                const hasPending = pendingTrips.has(row.tripId);
                return (
                  <li key={row.tripId} className={styles.item}>
                    <div className={styles.itemHead}>
                      <b>{formatLongDate(row.firstAt)}</b>
                      <StatusBadge group="attendance" value={row.status} />
                    </div>
                    <div className={styles.events}>
                      {row.events.map((ev) => (
                        <span key={ev.id} className={styles.event}>
                          <StatusBadge group="attendanceEvent" value={ev.eventType} />
                          <span className="mono">{formatClock(ev.recordedAt)}</span>
                          {ev.reason && <span className={styles.reason}>“{ev.reason}”</span>}
                        </span>
                      ))}
                    </div>
                    {isParent && (
                      <div className={styles.itemFoot}>
                        {hasPending ? (
                          <StatusBadge tone="warn" icon="pi-clock" label="İnceleme bekliyor" />
                        ) : (
                          <Button
                            type="button"
                            icon="pi pi-search"
                            label="İnceleme iste"
                            className={`p-button-text ${styles.tap}`}
                            onClick={() => setReviewRow(row)}
                            aria-label={`${formatLongDate(row.firstAt)} kaydı için inceleme iste`}
                          />
                        )}
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
            {isParent && <ReviewRequestDialog visible={Boolean(reviewRow)} onHide={() => setReviewRow(null)} row={reviewRow} student={student} />}
          </>
        );
      }}
    </QueryState>
  );
}

function useRange() {
  const today = todayISO();
  return useState({ from: addDaysISO(today, -30), to: today });
}

function ParentHistory() {
  const { query, children, selected, select } = useSelectedChild();
  const [range, setRange] = useRange();
  return (
    <>
      <PageMeta title="Yoklama Geçmişi" />
      <div className={styles.page}>
        <QueryState
          query={query}
          isEmpty={() => children.length === 0}
          forbiddenHome="/servisim"
          empty={<ScreenState kind="empty" icon="pi-user-plus" title="Aktif öğrenciniz yok" />}
        >
          {() => (
            <>
              <ChildSwitcher items={children} selectedId={selected?.id} onSelect={select} />
              <Notice tone="muted">
                Bir kayıt hatalıysa “İnceleme iste” ile yöneticiye iletebilirsiniz. Sonucu bildirim ve Taleplerim ekranında görürsünüz.
              </Notice>
              <RangeFilter from={range.from} to={range.to} onChange={setRange} />
              {selected && <HistoryList key={selected.id} student={selected} role="PARENT" range={range} />}
            </>
          )}
        </QueryState>
      </div>
    </>
  );
}

function StudentHistory() {
  const self = useSelfStudent();
  const [range, setRange] = useRange();
  return (
    <>
      <PageMeta title="Yoklama Geçmişim" />
      <div className={styles.page}>
        <QueryState
          query={self}
          isEmpty={() => !self.student}
          forbiddenHome="/servisim"
          empty={<ScreenState kind="empty" icon="pi-id-card" title="Öğrenci kaydın bağlı değil" />}
        >
          {() => (
            <>
              <RangeFilter from={range.from} to={range.to} onChange={setRange} />
              <HistoryList student={self.student} role="STUDENT" range={range} />
            </>
          )}
        </QueryState>
      </div>
    </>
  );
}

/** /yoklama — PARENT "Yoklama Geçmişi" (çocuk seçici + inceleme talebi); STUDENT "Yoklama Geçmişim". */
export default function AttendanceHistoryScreen() {
  const { role } = useAuth();
  if (role === "STUDENT") return <StudentHistory />;
  return (
    <Suspense fallback={<ScreenState kind="loading" rows={3} />}>
      <ParentHistory />
    </Suspense>
  );
}

