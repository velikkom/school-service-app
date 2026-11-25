"use client";

import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import { useAuth } from "@/providers/AuthProvider";
import { addDaysISO, formatLongDate, todayISO } from "@/lib/formatting/date";
import { pickActiveTrip, rosterCounts } from "@/features/trips/lib/fieldUtils";
import { useMyTrips, useRoster } from "@/features/trips/hooks/useTripQueries";
import { ActiveTripStrip, TripCard } from "./TripParts";
import styles from "@/features/trips/style/trips.module.scss";

function ActiveStrip({ trip }) {
  const roster = useRoster(trip.id);
  return <ActiveTripStrip trip={trip} counts={roster.data ? rosterCounts(roster.data) : null} />;
}

/** DRIVER/HOSTESS "Bugün": aktif sefer şeridi + bugünkü ve yarınki seferler (`trips/mine?businessDate=`). */
export default function FieldTodayScreen() {
  const { user } = useAuth();
  const today = todayISO();
  const tomorrow = addDaysISO(today, 1);
  const todayQ = useMyTrips(today);
  const tomorrowQ = useMyTrips(tomorrow);
  const firstName = user?.fullName ? user.fullName.split(" ")[0] : null;
  const active = todayQ.data ? pickActiveTrip(todayQ.data) : null;

  return (
    <>
      <PageMeta title="Bugün" subtitle={firstName ? `Merhaba, ${firstName} · ${formatLongDate(today)}` : formatLongDate(today)} />
      <div className={styles.narrow}>
        <QueryState
          query={todayQ}
          isEmpty={(d) => d.length === 0}
          empty={
            <ScreenState
              kind="empty"
              icon="pi-car"
              title="Bugün atanmış seferiniz yok"
              message="Seferler yönetici tarafından atanır. Değişiklikte bildirim alırsınız."
            />
          }
        >
          {(trips) => (
            <>
              {active ? (
                <ActiveStrip trip={active} />
              ) : (
                <ScreenState kind="empty" icon="pi-moon" title="Bugün başka seferiniz yok" message="Bugünkü seferleriniz tamamlandı veya iptal edildi." compact />
              )}
              <div className={styles.sectionTitle}>
                <h2>Bugünkü seferlerim</h2>
                <span className={styles.meta}>{trips.length} sefer</span>
              </div>
              <div className="stack">
                {trips.map((t) => (
                  <TripCard key={t.id} trip={t} href={`/seferler/${t.id}`} />
                ))}
              </div>
            </>
          )}
        </QueryState>

        <div className={styles.sectionTitle}>
          <h2>Yarın</h2>
        </div>
        <QueryState
          query={tomorrowQ}
          loadingRows={2}
          isEmpty={(d) => d.length === 0}
          empty={<p className={styles.meta} style={{ margin: 0 }}>Planlı sefer yok.</p>}
        >
          {(trips) => (
            <div className="stack">
              {trips.map((t) => (
                <TripCard key={t.id} trip={t} href={`/seferler/${t.id}`} />
              ))}
            </div>
          )}
        </QueryState>
      </div>
    </>
  );
}
