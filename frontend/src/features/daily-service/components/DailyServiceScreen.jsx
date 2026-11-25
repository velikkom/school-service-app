"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import Notice, { Avatar } from "@/components/ui/Notice";
import { confirmAction } from "@/components/feedback/feedback";
import { useAuth } from "@/providers/AuthProvider";
import { formatLongDate, todayISO } from "@/lib/formatting/date";
import ChildSwitcher from "@/components/ui/ChildSwitcher";
import { useSelectedChild } from "@/features/students/hooks/useSelectedChild";
import { useSelfStudent } from "@/features/students/hooks/useMyStudents";
import { useDailyService } from "@/features/daily-service/hooks/useDailyService";
import { pickHero } from "@/features/daily-service/hooks/dailyServiceModel";
import { useAbsenceNoticeActions } from "@/features/daily-service/action/useAbsenceNoticeActions";
import ServiceCard from "./ServiceCard";
import AbsenceNoticeDialog from "./AbsenceNoticeDialog";
import styles from "../style/dailyService.module.scss";

/** Seçili öğrencinin bugünkü servisleri: hero kart + günün diğer servisi. */
export function DailyServiceBody({ student, role }) {
  const today = todayISO();
  const query = useDailyService(student.id, today);
  const { submit, withdraw } = useAbsenceNoticeActions(student.id);
  const [dialogEntry, setDialogEntry] = useState(null);

  const onWithdraw = async (entry) => {
    const ok = await confirmAction({
      title: "Bildirimi geri al",
      message: "“Servise binmeyecek” bildiriminiz kaldırılır; görevli öğrenciyi durakta bekler.",
      acceptLabel: "Geri al",
      tone: "info",
    });
    if (ok) withdraw.mutate({ tripId: entry.tripId, noticeId: entry.absenceNotice.id });
  };

  return (
    <QueryState
      query={query}
      loadingRows={3}
      forbiddenHome="/servisim"
      isEmpty={(d) => !d?.length}
      notFound={<ScreenState kind="notFound" message="Bu öğrencinin servis bilgisine erişilemiyor." />}
      empty={
        <ScreenState
          kind="empty"
          icon="pi-calendar"
          title={role === "STUDENT" ? "Bugün servisin yok" : "Bugün servis yok"}
          message={`${formatLongDate(today)} için ${role === "STUDENT" ? "planlanmış seferin" : `${student.fullName} adına planlanmış sefer`} bulunmuyor.`}
        />
      }
    >
      {(entries) => {
        const hero = pickHero(entries);
        const others = entries.filter((e) => e !== hero);
        const busy = submit.isPending || withdraw.isPending;
        return (
          <>
            <ServiceCard entry={hero} hero role={role} busy={busy} onSubmitNotice={setDialogEntry} onWithdrawNotice={onWithdraw} />
            {others.length > 0 && <h2 className={styles.sectionTitle}>Günün diğer servisi</h2>}
            {others.map((e) => (
              <ServiceCard key={e.tripId} entry={e} role={role} busy={busy} onSubmitNotice={setDialogEntry} onWithdrawNotice={onWithdraw} />
            ))}
            {role === "PARENT" && (
              <AbsenceNoticeDialog
                visible={Boolean(dialogEntry)}
                onHide={() => setDialogEntry(null)}
                entry={dialogEntry}
                studentName={student.fullName}
                submitting={submit.isPending}
                onSubmit={(note) =>
                  submit.mutate({ tripId: dialogEntry.tripId, note }, { onSuccess: () => setDialogEntry(null) })
                }
              />
            )}
          </>
        );
      }}
    </QueryState>
  );
}

function ParentDailyService() {
  const { query, children, selected, select } = useSelectedChild();
  return (
    <>
      <PageMeta title="Bugünkü Servis" subtitle={formatLongDate(todayISO())} />
      <div className={styles.page}>
        <QueryState
          query={query}
          isEmpty={() => children.length === 0}
          forbiddenHome="/servisim"
          empty={
            <ScreenState
              kind="empty"
              icon="pi-user-plus"
              title="Aktif öğrenciniz yok"
              message="Başvurunuz onaylandığında servis bilgisi burada görünür."
              action={
                <Link href="/ogrenci-ekle" className="p-button">
                  <i className="pi pi-plus" aria-hidden="true" /> Öğrenci ekle
                </Link>
              }
            />
          }
        >
          {() => (
            <>
              <ChildSwitcher items={children} selectedId={selected?.id} onSelect={select} />
              {selected && <DailyServiceBody key={selected.id} student={selected} role="PARENT" />}
            </>
          )}
        </QueryState>
      </div>
    </>
  );
}

function StudentDailyService() {
  const self = useSelfStudent();
  return (
    <>
      <PageMeta title="Bugünkü Servisim" subtitle={formatLongDate(todayISO())} />
      <div className={styles.page}>
        <QueryState
          query={self}
          isEmpty={() => !self.student}
          forbiddenHome="/servisim"
          empty={<ScreenState kind="empty" icon="pi-id-card" title="Öğrenci kaydın bağlı değil" message="Hesabına bağlı bir öğrenci kaydı bulunamadı. Kurum yöneticisiyle iletişime geç." />}
        >
          {() => (
            <>
              <div className={styles.studentHead}>
                <Avatar name={self.student.fullName} size={40} />
                <div className={styles.minw0}>
                  <b>{self.student.fullName}</b>
                  <div className={styles.small}>{[self.student.schoolName, self.student.grade].filter(Boolean).join(" · ") || "—"}</div>
                </div>
              </div>
              <DailyServiceBody student={self.student} role="STUDENT" />
              <Notice tone="muted">Servise binmeyeceksen velinden bildirmesini iste. Öğrenci hesabı yalnızca görüntüler.</Notice>
            </>
          )}
        </QueryState>
      </div>
    </>
  );
}

/** /servisim — PARENT: çocuk seçicili bugünkü servis + bildirim; STUDENT: salt okunur. */
export default function DailyServiceScreen() {
  const { role } = useAuth();
  if (role === "STUDENT") return <StudentDailyService />;
  return (
    <Suspense fallback={<ScreenState kind="loading" rows={3} />}>
      <ParentDailyService />
    </Suspense>
  );
}
