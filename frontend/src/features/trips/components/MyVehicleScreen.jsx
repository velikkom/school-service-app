"use client";

import { useQuery } from "@tanstack/react-query";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import Card, { DefinitionList } from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import { vehiclesService } from "@/features/vehicles/service/vehiclesService";
import { formatTime, todayISO } from "@/lib/formatting/date";
import { labelOf } from "@/lib/formatting/labels";
import { useMyTrips } from "@/features/trips/hooks/useTripQueries";
import { tripKeys } from "@/features/trips/hooks/keys";
import styles from "@/features/trips/style/trips.module.scss";

/** vehicles/mine: 404 = atanmış araç yok (boş durum, hata değil). */
function useMyVehicle() {
  return useQuery({
    queryKey: tripKeys.myVehicle(),
    staleTime: 60_000,
    queryFn: async () => {
      try {
        return await vehiclesService.mine();
      } catch (err) {
        if (err?.status === 404) return null;
        throw err;
      }
    },
  });
}

/**
 * DRIVER "Aracım": bugünkü seferin aracı (plaka `vehiclePlate`), ayrıntı `GET /api/vehicles/mine`.
 * `vehicles/mine` "bugünkü" değil en son seferin aracıdır (API ref F-6). Muayene/bakım alanı API'de yok.
 */
export default function MyVehicleScreen() {
  const today = todayISO();
  const trips = useMyTrips(today);
  const mine = useMyVehicle();
  const todays = (trips.data ?? []).filter((t) => t.status !== "CANCELLED" && t.vehicleId);
  const todayTrip = todays[0] ?? null;
  const failed = trips.isError ? trips : mine.isError ? mine : null;

  let body;
  if (failed) body = <ScreenState kind="error" error={failed.error} onRetry={failed.refetch} />;
  else if (trips.isPending || mine.isPending) body = <ScreenState kind="loading" rows={2} />;
  else if (!todayTrip && !mine.data)
    body = (
      <ScreenState
        kind="empty"
        icon="pi-car"
        title="Atanmış araç yok"
        message="Size atanmış bir sefer ve araç bulunmuyor. Atama yapıldığında araç burada görünür."
      />
    );
  else {
    const vehicle = mine.data && (!todayTrip || mine.data.id === todayTrip.vehicleId) ? mine.data : null;
    const plate = todayTrip?.vehiclePlate ?? vehicle?.plate;
    const vehicleTrips = todayTrip ? todays.filter((t) => t.vehicleId === todayTrip.vehicleId) : [];
    body = (
      <Card>
        <div className="stack-lg">
          <div className="between">
            <VehiclePlate plate={plate} />
            {vehicle && <StatusBadge group="active" value={String(vehicle.isActive)} />}
          </div>
          <DefinitionList
            items={[
              vehicle && ["Model", vehicle.model || "—"],
              vehicle && ["Kapasite", vehicle.capacity ? `${vehicle.capacity} yolcu` : "—"],
              todayTrip
                ? [
                    "Bugünkü seferler",
                    vehicleTrips.map((t) => `${labelOf("direction", t.direction).short} ${formatTime(t.plannedStartTime)}`).join(", "),
                  ]
                : null,
            ]}
          />
          {!todayTrip && (
            <Notice tone="info">Bugün seferiniz yok. Gösterilen araç, size atanmış en son seferin aracıdır.</Notice>
          )}
          {todays.some((t) => t.vehicleId !== todayTrip?.vehicleId) && (
            <Notice tone="warn">Bugünkü seferlerinizden bazıları farklı araçla planlanmış; plakayı sefer kartında kontrol edin.</Notice>
          )}
          <p className={styles.meta} style={{ margin: 0 }}>
            Salt okunur. Araç bilgisinde değişiklik için yöneticinize başvurun.
          </p>
        </div>
      </Card>
    );
  }

  return (
    <>
      <PageMeta title="Aracım" />
      <div className={styles.narrow}>{body}</div>
    </>
  );
}
