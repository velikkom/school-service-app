"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import { todayISO } from "@/lib/formatting/date";
import { pickActiveTrip } from "@/features/trips/lib/fieldUtils";
import { useMyTrips } from "@/features/trips/hooks/useTripQueries";

const TITLES = { yoklama: "Yoklama", konum: "Konum" };

/**
 * DRIVER/HOSTESS /yoklama ve /konum: bugünkü yoldaki sefere (yoksa ilk planlı sefere)
 * `/seferler/{id}?tab=...` ile yönlenir; hiç yoksa açıklayıcı boş durum gösterir.
 */
export default function FieldActiveTripRedirect({ tab = "yoklama" }) {
  const router = useRouter();
  const query = useMyTrips(todayISO());
  const target = query.data ? pickActiveTrip(query.data) : null;

  useEffect(() => {
    if (target) router.replace(`/seferler/${target.id}?tab=${tab}`);
  }, [target, tab, router]);

  const title = TITLES[tab] ?? "Sefer";

  if (query.isError) {
    return (
      <>
        <PageMeta title={title} />
        <ScreenState kind="error" error={query.error} onRetry={query.refetch} />
      </>
    );
  }

  if (query.isPending || target) {
    return (
      <>
        <PageMeta title={title} />
        <ScreenState kind="loading" title="Aktif sefer açılıyor" rows={2} />
      </>
    );
  }

  return (
    <>
      <PageMeta title={title} />
      <ScreenState
        kind="empty"
        icon={tab === "konum" ? "pi-map-marker" : "pi-check-circle"}
        title="Aktif sefer yok"
        message={
          tab === "konum"
            ? "Konum, bugünkü başlatılmış seferde paylaşılır. Bugün için planlı veya yoldaki seferiniz bulunmuyor."
            : "Yoklama, bugünkü seferin ekranında girilir. Bugün için planlı veya yoldaki seferiniz bulunmuyor."
        }
        action={
          <Link href="/seferler" className="p-button">
            Bugün ekranına dön
          </Link>
        }
      />
    </>
  );
}
