"use client";

import { useQuery } from "@tanstack/react-query";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import Notice from "@/components/ui/Notice";
import { labelOf } from "@/lib/formatting/labels";
import { formatDate, formatTime, todayISO } from "@/lib/formatting/date";
import { toList } from "@/lib/http/page";
import { assignmentsService } from "@/features/assignments/service/assignmentsService";
import { dailyServiceService } from "@/features/daily-service/service/dailyServiceService";
import { useSelfStudent } from "@/features/students/hooks/useMyStudents";
import { familyKeys } from "@/features/family/hooks/keys";
import styles from "../style/family.module.scss";

/**
 * Atamaya karşılık gelen bugünkü sefer satırı: önce aynı yön + aynı durak; bulunamazsa ve atama bugün
 * geçerliyse aynı yöndeki sefer (günlük servis o günkü geçerli atamadan üretilir).
 */
export function matchDailyEntry(assignment, entries, today) {
  const list = entries ?? [];
  const exact = list.find((e) => e.direction === assignment.direction && e.stopId === assignment.stopId);
  if (exact) return exact;
  const current = (!assignment.validFrom || assignment.validFrom <= today) && (!assignment.validTo || assignment.validTo >= today);
  return current ? list.find((e) => e.direction === assignment.direction) ?? null : null;
}

function RouteBody({ student }) {
  const today = todayISO();
  const assignments = useQuery({
    queryKey: familyKeys.assignments(student.id),
    queryFn: async () => toList(await assignmentsService.list({ studentId: student.id, size: 20 })),
  });
  // Günlük servis anahtarı daily-service ekranıyla aynı biçimde: ["daily-service", id, tarih]
  const daily = useQuery({
    queryKey: ["daily-service", Number(student.id), today],
    queryFn: () => dailyServiceService.forStudent(student.id, today),
  });

  return (
    <QueryState
      query={assignments}
      isEmpty={(d) => !d?.filter((a) => !a.validTo || a.validTo >= today).length}
      forbiddenHome="/servisim"
      empty={<ScreenState kind="empty" icon="pi-directions" title="Güzergâh ataman yok" message="Kurum seni bir güzergâha atadığında durak bilgin burada görünür." />}
    >
      {(list) => {
        const active = list
          .filter((a) => !a.validTo || a.validTo >= today)
          .sort((a, b) => (a.direction === b.direction ? 0 : a.direction === "MORNING" ? -1 : 1));
        const anyMissing = active.some((a) => !matchDailyEntry(a, daily.data, today));
        return (
          <>
            {active.map((a) => {
              const dir = labelOf("direction", a.direction);
              const e = matchDailyEntry(a, daily.data, today);
              return (
                <article key={a.id} className={styles.itemCard}>
                  <div className={styles.itemHead}>
                    <span className={styles.itemTitle}>
                      <i className={`pi ${dir.icon}`} aria-hidden="true" /> {dir.label}
                    </span>
                    <span className={styles.meta}>
                      {a.validFrom > today ? `${formatDate(a.validFrom)} itibarıyla geçerli olacak` : `${formatDate(a.validFrom)} itibarıyla`}
                    </span>
                  </div>
                  <div className={styles.small}>{e?.routeName ?? "Güzergâh adı bugün görüntülenemiyor"}</div>
                  <div className={styles.stopline}>
                    <i className="pi pi-map-marker" aria-hidden="true" />
                    <div>
                      <b>{e?.stopName ?? "Durak adı bugün görüntülenemiyor"}</b>
                      <div className={styles.small}>
                        Durakta planlanan saat <span className={styles.bigTime}>{e ? formatTime(e.stopPlannedTime) : "—"}</span>
                      </div>
                    </div>
                  </div>
                </article>
              );
            })}
            {daily.isPending && <ScreenState kind="loading" rows={1} compact />}
            {!daily.isPending && anyMissing && (
              <Notice tone="muted" title="Durak adı ve saati">
                Durak adı ve saati yalnızca sefer olan günlerde (bugünkü servisinden) gösterilebilir. Bugün bu yön için sefer yoksa
                bilgiler sefer olan bir günde burada görünür.
              </Notice>
            )}
          </>
        );
      }}
    </QueryState>
  );
}

/** /atamalar (STUDENT) "Güzergâhım" — kendi ataması + bugünkü seferden durak adı/saati. */
export default function StudentRouteScreen() {
  const self = useSelfStudent();
  return (
    <>
      <PageMeta title="Güzergâhım" />
      <div className={styles.page}>
        <QueryState
          query={self}
          isEmpty={() => !self.student}
          forbiddenHome="/servisim"
          empty={<ScreenState kind="empty" icon="pi-id-card" title="Öğrenci kaydın bağlı değil" />}
        >
          {() => <RouteBody student={self.student} />}
        </QueryState>
      </div>
    </>
  );
}
