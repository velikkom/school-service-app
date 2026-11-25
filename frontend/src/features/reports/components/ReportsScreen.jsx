"use client";

import { useState } from "react";
import { InputText } from "primereact/inputtext";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import Card from "@/components/ui/Card";
import { formatDate, todayISO } from "@/lib/formatting/date";
import { formatMoney } from "@/lib/formatting/money";
import { PAYMENT_METHOD_LABELS } from "@/lib/formatting/labels";
import { methodBreakdown, useCollectionsReport, useDailySummaryReport, useReceivablesReport } from "../hooks/useReports";
import styles from "../style/reports.module.scss";

const METHODS = Object.keys(PAYMENT_METHOD_LABELS);

function QueryBlock({ query, rows = 2, children }) {
  if (query.isPending && !query.data) return <ScreenState kind="loading" rows={rows} bare />;
  if (query.isError && !query.data) {
    if (query.error?.status === 403) return <ScreenState kind="forbidden" homeHref="/dashboard" bare compact />;
    return <ScreenState kind="error" error={query.error} onRetry={query.refetch} bare compact />;
  }
  return children(query.data);
}

function DateInput({ id, label, value, onChange, max }) {
  return (
    <div className={styles.dateField}>
      <label htmlFor={id}>{label}</label>
      <InputText id={id} type="date" value={value} max={max} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

/** ADMIN "Raporlar": günlük operasyon özeti, tahsilat (yönteme göre), alacak durumu. */
export default function ReportsScreen() {
  const today = todayISO();
  const [date, setDate] = useState(today);
  const [from, setFrom] = useState(`${today.slice(0, 8)}01`);
  const [to, setTo] = useState(today);
  const rangeError = from && to && from > to ? "Başlangıç tarihi bitişten sonra olamaz." : null;

  const summary = useDailySummaryReport(date || today);
  const collections = useCollectionsReport(from, to, { enabled: !rangeError });
  const receivables = useReceivablesReport();

  return (
    <>
      <PageMeta title="Raporlar" />
      <div className="stack-lg">
        <Card title="Günlük operasyon özeti" subtitle={formatDate(date || today)} actions={<DateInput id="rep-date" label="Tarih" value={date} onChange={setDate} />}>
          <QueryBlock query={summary}>
            {(s) => (
              <div className={styles.stats}>
                {[
                  ["Toplam sefer", s.totalTrips],
                  ["Planlı", s.plannedCount],
                  ["Yolda", s.inProgressCount],
                  ["Tamamlanan", s.completedCount],
                  ["İptal", s.cancelledCount],
                  ["Biniş", s.boardingCount],
                  ["İniş", s.dropoffCount],
                  ["Devamsızlık", s.absenceCount],
                ].map(([label, value]) => (
                  <div key={label} className={styles.stat}>
                    <span className={styles.statLabel}>{label}</span>
                    <span className={styles.statValue}>{value ?? 0}</span>
                  </div>
                ))}
              </div>
            )}
          </QueryBlock>
        </Card>

        <div className="grid-2">
          <Card title="Tahsilat" subtitle="Tarihler dahil">
            <div className="stack-lg">
              <div className={styles.filters}>
                <DateInput id="rep-from" label="Başlangıç" value={from} onChange={setFrom} />
                <DateInput id="rep-to" label="Bitiş" value={to} onChange={setTo} />
              </div>
              {rangeError ? (
                <span className={styles.error} role="alert">
                  {rangeError}
                </span>
              ) : (
                <QueryBlock query={collections} rows={3}>
                  {(c) => (
                    <div className="stack-lg">
                      <div className={styles.stat}>
                        <span className={styles.statLabel}>Toplam tahsilat</span>
                        <span className={styles.statMoney}>{formatMoney(c.totalCollected)}</span>
                      </div>
                      {methodBreakdown(c, METHODS).map((m) => (
                        <div key={m.method} className={styles.bar}>
                          <div className={styles.barHead}>
                            <span>{PAYMENT_METHOD_LABELS[m.method]}</span>
                            <b className="mono">
                              {formatMoney(m.amount)} <span className="muted text-xs">%{m.pct}</span>
                            </b>
                          </div>
                          <div className={styles.track} aria-hidden="true">
                            <div className={styles.fill} style={{ width: `${m.pct}%` }} />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </QueryBlock>
              )}
            </div>
          </Card>

          <Card title="Alacak durumu" subtitle="Tüm dönemler" footer={<span className="text-xs muted">Öğrenci bazında kırılım raporda yok; öğrenci ekstresinden izlenir.</span>}>
            <QueryBlock query={receivables}>
              {(r) => (
                <div className={styles.money3}>
                  {[
                    ["Tahakkuk", r.totalCharged],
                    ["Tahsilat", r.totalCollected],
                    ["Alacak", r.totalReceivable],
                  ].map(([label, value]) => (
                    <div key={label} className={styles.stat}>
                      <span className={styles.statLabel}>{label}</span>
                      <span className={styles.statMoney}>{formatMoney(value)}</span>
                    </div>
                  ))}
                </div>
              )}
            </QueryBlock>
          </Card>
        </div>
      </div>
    </>
  );
}
