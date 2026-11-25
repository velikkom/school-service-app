"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import StatusBadge from "@/components/ui/StatusBadge";
import Notice from "@/components/ui/Notice";
import { labelOf } from "@/lib/formatting/labels";
import { formatDate, formatRelativeDateTime } from "@/lib/formatting/date";
import { toList } from "@/lib/http/page";
import { attendanceCorrectionsService } from "@/features/attendance-corrections/service/attendanceCorrectionsService";
import { contractsService } from "@/features/contracts/service/contractsService";
import { useMyStopChangeRequests } from "@/features/stop-change-requests/hooks/useStopChangeRequests";
import StopChangeRequestDialog from "@/features/stop-change-requests/components/StopChangeRequestDialog";
import { useMyStudents } from "@/features/students/hooks/useMyStudents";
import { familyKeys } from "@/features/family/hooks/keys";
import styles from "../style/family.module.scss";

const TABS = [
  { key: "durak", label: "Durak değişikliği" },
  { key: "yoklama", label: "Yoklama incelemeleri" },
  { key: "basvuru", label: "Öğrenci başvuruları" },
];

function useCorrections() {
  return useQuery({
    queryKey: familyKeys.corrections(),
    queryFn: async () => toList(await attendanceCorrectionsService.list({ size: 50, sort: "createdAt,desc" })),
  });
}

function usePendingEnrollments() {
  return useQuery({
    queryKey: familyKeys.pendingContracts(),
    queryFn: async () => toList(await contractsService.list({ status: "PENDING_APPROVAL", size: 50 })),
  });
}

function DecisionNote({ status, note }) {
  if (!note) return null;
  return (
    <Notice tone={status === "APPROVED" ? "ok" : "danger"} title={status === "APPROVED" ? "Yönetici notu" : "Ret gerekçesi"}>
      {note}
    </Notice>
  );
}

function StopChangeTab() {
  const query = useMyStopChangeRequests();
  const students = useMyStudents();
  const [open, setOpen] = useState(false);
  const active = (students.data ?? []).filter((s) => s.isActive !== false);

  return (
    <>
      <div className={styles.actionsRow}>
        <Button type="button" icon="pi pi-plus" label="Durak değişikliği iste" onClick={() => setOpen(true)} disabled={!active.length} />
      </div>
      <QueryState
        query={query}
        isEmpty={(d) => !d?.length}
        forbiddenHome="/servisim"
        empty={<ScreenState kind="empty" icon="pi-map-marker" title="Durak talebiniz yok" message="Çocuğunuzun biniş/iniş durağını değiştirmek için yeni talep açabilirsiniz." />}
      >
        {(rows) => (
          <div className={styles.page}>
            {rows.map((r) => (
              <article key={r.id} className={styles.itemCard}>
                <div className={styles.itemHead}>
                  <span className={styles.itemTitle}>
                    {r.studentFullName} · {labelOf("direction", r.direction).short}
                  </span>
                  <StatusBadge group="requestStatus" value={r.status} />
                </div>
                <div className={styles.small}>
                  Yeni durak: <b>{r.requestedStopName}</b>
                  {r.requestedRouteName ? ` (${r.requestedRouteName})` : ""} · {formatDate(r.effectiveDate)} itibarıyla
                </div>
                {r.currentStopName && <div className={styles.small}>Mevcut durak: {r.currentStopName}</div>}
                {r.reason && <div className={styles.quote}>“{r.reason}”</div>}
                <DecisionNote status={r.status} note={r.decisionNote} />
                <div className={styles.meta}>
                  Gönderildi {formatRelativeDateTime(r.createdAt)}
                  {r.decidedAt ? ` · Karar ${formatRelativeDateTime(r.decidedAt)}` : ""}
                </div>
              </article>
            ))}
          </div>
        )}
      </QueryState>
      <StopChangeRequestDialog visible={open} onHide={() => setOpen(false)} students={active} />
    </>
  );
}

function CorrectionsTab() {
  const query = useCorrections();
  return (
    <QueryState
      query={query}
      isEmpty={(d) => !d?.length}
      forbiddenHome="/servisim"
      empty={
        <ScreenState
          kind="empty"
          icon="pi-history"
          title="İnceleme talebiniz yok"
          message="Yoklama Geçmişi ekranından bir kayıt için inceleme isteyebilirsiniz."
          action={
            <Link href="/yoklama" className="p-button p-button-secondary">
              Yoklama Geçmişi
            </Link>
          }
        />
      }
    >
      {(rows) => (
        <div className={styles.page}>
          {rows.map((c) => (
            <article key={c.id} className={styles.itemCard}>
              <div className={styles.itemHead}>
                <span className={styles.itemTitle}>
                  {c.studentFullName} · {formatDate(c.tripBusinessDate)} · {labelOf("direction", c.tripDirection).short}
                </span>
                <StatusBadge group="requestStatus" value={c.status} />
              </div>
              <div className={styles.badges}>
                <StatusBadge group="correctionKind" value={c.kind} />
                <span className={styles.small}>Kayıt:</span>
                <StatusBadge group="attendance" value={c.sourceStatus} />
                <i className="pi pi-arrow-right" aria-hidden="true" />
                {c.requestedStatus ? <StatusBadge group="attendance" value={c.requestedStatus} /> : <span className={styles.small}>Kontrol edilsin</span>}
              </div>
              {c.reason && <div className={styles.quote}>“{c.reason}”</div>}
              {c.status === "APPROVED" && c.appliedStatus && (
                <div className={styles.small}>
                  Uygulanan durum: <b>{labelOf("attendance", c.appliedStatus).label}</b>
                </div>
              )}
              <DecisionNote status={c.status} note={c.decisionNote} />
              <div className={styles.meta}>
                Gönderildi {formatRelativeDateTime(c.createdAt)}
                {c.decidedAt ? ` · Karar ${formatRelativeDateTime(c.decidedAt)}` : ""}
              </div>
            </article>
          ))}
        </div>
      )}
    </QueryState>
  );
}

function EnrollmentsTab() {
  const query = usePendingEnrollments();
  return (
    <>
      <QueryState
        query={query}
        isEmpty={(d) => !d?.length}
        forbiddenHome="/servisim"
        empty={
          <ScreenState
            kind="empty"
            icon="pi-user-plus"
            title="Bekleyen başvuru yok"
            message="Yeni çocuk eklemek için “Öğrenci ekle”yi kullanın."
            action={
              <Link href="/ogrenci-ekle" className="p-button">
                <i className="pi pi-plus" aria-hidden="true" /> Öğrenci ekle
              </Link>
            }
          />
        }
      >
        {(rows) => (
          <div className={styles.page}>
            {rows.map((c) => (
              <article key={c.id} className={styles.itemCard}>
                <div className={styles.itemHead}>
                  <span className={styles.itemTitle}>{c.studentFullName}</span>
                  <StatusBadge group="contractStatus" value={c.status} />
                </div>
                {c.startDate && <div className={styles.small}>Başlangıç: {formatDate(c.startDate)}</div>}
                <div className={styles.small}>Yönetici onayladığında bildirim alırsınız. Bu sürede yeni başvuru açılamaz.</div>
              </article>
            ))}
          </div>
        )}
      </QueryState>
      <Notice tone="muted" icon="pi-bell" title="Reddedilen başvurular">
        Ret sonrası öğrenci kaydı pasife alınır ve bu listeden düşer; sonuç ve gerekçe <Link href="/bildirimler">Bildirimler</Link>&apos;de
        kalır.
      </Notice>
    </>
  );
}

function MyRequestsInner() {
  const params = useSearchParams();
  const tab = TABS.some((t) => t.key === params.get("tab")) ? params.get("tab") : "durak";
  return (
    <>
      <PageMeta title="Taleplerim" />
      <div className={styles.page}>
        <nav className={styles.tabs} aria-label="Talep türleri">
          {TABS.map((t) => (
            <Link
              key={t.key}
              href={`/taleplerim?tab=${t.key}`}
              scroll={false}
              replace
              className={`${styles.tab} ${tab === t.key ? styles.tabOn : ""}`}
              aria-current={tab === t.key ? "page" : undefined}
            >
              {t.label}
            </Link>
          ))}
        </nav>
        {tab === "durak" && <StopChangeTab />}
        {tab === "yoklama" && <CorrectionsTab />}
        {tab === "basvuru" && <EnrollmentsTab />}
      </div>
    </>
  );
}

/** /taleplerim (PARENT) — `?tab=durak|yoklama|basvuru`. */
export default function MyRequestsScreen() {
  return (
    <Suspense fallback={<ScreenState kind="loading" rows={3} />}>
      <MyRequestsInner />
    </Suspense>
  );
}
