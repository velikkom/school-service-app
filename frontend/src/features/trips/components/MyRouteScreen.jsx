"use client";

import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import Card from "@/components/ui/Card";
import { labelOf } from "@/lib/formatting/labels";
import { addDaysISO, todayISO } from "@/lib/formatting/date";
import { routesFromTrips } from "@/features/trips/lib/fieldUtils";
import { useMyTrips, useRouteStops } from "@/features/trips/hooks/useTripQueries";
import { StopTimeline } from "./TripParts";
import styles from "@/features/trips/style/trips.module.scss";

function RouteCard({ route }) {
  const stops = useRouteStops(route.routeId);
  const dir = labelOf("direction", route.direction);
  return (
    <Card
      title={route.routeName}
      subtitle={`${dir.label}${stops.data ? ` · ${stops.data.length} durak` : ""} · ${route.when.join(", ")}`}
    >
      <QueryState query={stops} loadingRows={3}>
        {(data) => <StopTimeline stops={data} tripStatus={null} showCounts={false} />}
      </QueryState>
    </Card>
  );
}

/** DRIVER/HOSTESS "Güzergâhım": bugünkü ve yarınki seferlerin güzergâhları + sıralı duraklar (salt okunur). */
export default function MyRouteScreen() {
  const today = todayISO();
  const tomorrow = addDaysISO(today, 1);
  const todayQ = useMyTrips(today);
  const tomorrowQ = useMyTrips(tomorrow);
  const failed = todayQ.isError ? todayQ : tomorrowQ.isError ? tomorrowQ : null;
  const loading = todayQ.isPending || tomorrowQ.isPending;
  const routes = routesFromTrips([...(todayQ.data ?? []), ...(tomorrowQ.data ?? [])], today);

  let body;
  if (failed) body = <ScreenState kind="error" error={failed.error} onRetry={failed.refetch} />;
  else if (loading) body = <ScreenState kind="loading" rows={3} />;
  else if (!routes.length)
    body = (
      <ScreenState
        kind="empty"
        icon="pi-directions"
        title="Atanmış güzergâh yok"
        message="Bugün ve yarın için seferiniz bulunmuyor. Size sefer atandığında güzergâhı burada görürsünüz."
      />
    );
  else body = routes.map((r) => <RouteCard key={r.routeId} route={r} />);

  return (
    <>
      <PageMeta title="Güzergâhım" subtitle="Bugün ve yarın" />
      <div className={styles.narrow}>{body}</div>
    </>
  );
}
