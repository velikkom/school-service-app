"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import { toList } from "@/lib/http/page";
import { contractsService } from "@/features/contracts/service/contractsService";
import EnrollmentWizard from "./EnrollmentWizard";
import styles from "../style/enrollment.module.scss";

/** /ogrenci-ekle (PARENT) — ek çocuk başvurusu. Bekleyen başvuru varsa yeni başvuru açılamaz (409). */
export default function AddStudentScreen() {
  const router = useRouter();
  const pending = useQuery({
    queryKey: ["contracts", "mine", "PENDING_APPROVAL"],
    queryFn: async () => toList(await contractsService.list({ status: "PENDING_APPROVAL", size: 50 })),
  });

  return (
    <>
      <PageMeta title="Öğrenci Ekle" back="/ogrenciler" />
      <div className={styles.page}>
        <QueryState query={pending} loadingRows={3} forbiddenHome="/servisim">
          {(list) =>
            list.length ? (
              <ScreenState
                kind="empty"
                icon="pi-clock"
                title="Bekleyen başvurunuz var"
                message={`${list[0].studentFullName} başvurusu yönetici onayında. Sonuçlanana kadar yeni başvuru açılamaz.`}
                action={
                  <Link href="/taleplerim?tab=basvuru" className="p-button p-button-secondary">
                    Başvuruyu görüntüle
                  </Link>
                }
              />
            ) : (
              <section className={styles.gateCard}>
                <EnrollmentWizard mode="additional" onDone={() => router.push("/taleplerim?tab=basvuru")} />
              </section>
            )
          }
        </QueryState>
      </div>
    </>
  );
}
