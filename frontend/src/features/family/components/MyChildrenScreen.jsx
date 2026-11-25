"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import StatusBadge from "@/components/ui/StatusBadge";
import { DefinitionList } from "@/components/ui/Card";
import { Avatar } from "@/components/ui/Notice";
import { GENDER_LABELS, labelOf } from "@/lib/formatting/labels";
import { formatDate, todayISO } from "@/lib/formatting/date";
import { toList } from "@/lib/http/page";
import { assignmentsService } from "@/features/assignments/service/assignmentsService";
import { contractsService } from "@/features/contracts/service/contractsService";
import { useMyStudents } from "@/features/students/hooks/useMyStudents";
import { familyKeys } from "@/features/family/hooks/keys";
import styles from "../style/family.module.scss";

/** Öğrencinin gösterilecek sözleşme durumu: ACTIVE > PENDING_APPROVAL > en yeni. */
export function contractStatusOf(contracts) {
  if (!contracts?.length) return null;
  return (contracts.find((c) => c.status === "ACTIVE") ?? contracts.find((c) => c.status === "PENDING_APPROVAL") ?? contracts[0]).status;
}

function isCurrent(a, today) {
  return (!a.validFrom || a.validFrom <= today) && (!a.validTo || a.validTo >= today);
}

function ChildCard({ student, contracts, assignments }) {
  const today = todayISO();
  const status = contractStatusOf(contracts);
  const current = assignments.filter((a) => isCurrent(a, today));
  const upcoming = assignments.filter((a) => a.validFrom && a.validFrom > today);
  const address = [student.addressLine, student.neighborhood, student.district, student.city].filter(Boolean).join(", ");
  const pending = status === "PENDING_APPROVAL";

  return (
    <article className={styles.itemCard}>
      <div className={styles.childCardHead}>
        <Avatar name={student.fullName} size={44} />
        <div className={styles.childCardText}>
          <div className={styles.itemTitle}>{student.fullName}</div>
          <div className={styles.small}>{[student.schoolName, student.grade].filter(Boolean).join(" · ") || "Okul bilgisi yok"}</div>
        </div>
        {status ? <StatusBadge group="contractStatus" value={status} /> : <StatusBadge group="active" value={String(student.isActive !== false)} />}
      </div>

      <DefinitionList
        items={[
          ["Doğum tarihi", formatDate(student.birthDate)],
          student.gender ? ["Cinsiyet", GENDER_LABELS[student.gender] ?? "—"] : null,
          ["Adres", address || "—"],
          ["Sağlık notu", student.healthNote || "—"],
        ]}
      />

      {pending ? (
        <p className={styles.small}>Başvuru yönetici onayında. Onaylandığında servis ataması burada görünür.</p>
      ) : (
        <>
          <h3 className={styles.sectionTitle}>Servis ataması</h3>
          {current.length === 0 && upcoming.length === 0 ? (
            <p className={styles.small}>Henüz güzergâh ataması yapılmadı.</p>
          ) : (
            <div className={styles.assignGrid}>
              {[...current, ...upcoming].map((a) => {
                const dir = labelOf("direction", a.direction);
                return (
                  <div key={a.id} className={styles.assignBox}>
                    <span className={styles.small}>
                      <i className={`pi ${dir.icon}`} aria-hidden="true" /> {dir.label}
                    </span>
                    <span className={styles.meta}>
                      {a.validFrom && a.validFrom > today ? `${formatDate(a.validFrom)} itibarıyla geçerli olacak` : `${formatDate(a.validFrom)} itibarıyla`}
                      {a.validTo ? ` · ${formatDate(a.validTo)} tarihine kadar` : ""}
                    </span>
                  </div>
                );
              })}
            </div>
          )}
          <p className={styles.meta}>Durak adı ve saati sefer olan günlerde Bugünkü Servis ekranında gösterilir.</p>
        </>
      )}

      {!pending && student.isActive !== false && (
        <div className={styles.actionsRow}>
          <Link href={`/servisim?child=${student.id}`} className="p-button p-button-secondary">
            <i className="pi pi-car" aria-hidden="true" /> Bugünkü servis
          </Link>
          <Link href="/taleplerim?tab=durak" className="p-button p-button-text">
            <i className="pi pi-map-marker" aria-hidden="true" /> Durak değişikliği iste
          </Link>
        </div>
      )}
    </article>
  );
}

/** /ogrenciler (PARENT) "Çocuklarım" — salt okunur öğrenci bilgileri, başvuru durumu ve atamalar. */
export default function MyChildrenScreen() {
  const students = useMyStudents();
  const contracts = useQuery({
    queryKey: familyKeys.allContracts(),
    queryFn: async () => toList(await contractsService.list({ size: 100 })),
  });
  const assignments = useQuery({
    queryKey: familyKeys.assignments(null),
    queryFn: async () => toList(await assignmentsService.list({ size: 100 })),
  });

  return (
    <>
      <PageMeta title="Çocuklarım" />
      <div className={styles.page}>
        <QueryState
          query={students}
          isEmpty={(d) => !d?.length}
          forbiddenHome="/servisim"
          empty={
            <ScreenState
              kind="empty"
              icon="pi-user-plus"
              title="Kayıtlı çocuğunuz yok"
              action={
                <Link href="/ogrenci-ekle" className="p-button">
                  <i className="pi pi-plus" aria-hidden="true" /> Öğrenci ekle
                </Link>
              }
            />
          }
        >
          {(list) => (
            <>
              {(contracts.isError || assignments.isError) && (
                <ScreenState kind="error" compact title="Bazı bilgiler yüklenemedi" error={contracts.error ?? assignments.error} onRetry={() => { contracts.refetch(); assignments.refetch(); }} />
              )}
              {list.map((s) => (
                <ChildCard
                  key={s.id}
                  student={s}
                  contracts={(contracts.data ?? []).filter((c) => c.studentId === s.id)}
                  assignments={(assignments.data ?? []).filter((a) => a.studentId === s.id)}
                />
              ))}
              <Link href="/ogrenci-ekle" className={`p-button p-button-secondary ${styles.tap}`}>
                <i className="pi pi-plus" aria-hidden="true" /> Öğrenci ekle
              </Link>
            </>
          )}
        </QueryState>
      </div>
    </>
  );
}
