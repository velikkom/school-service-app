"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import Card, { DefinitionList } from "@/components/ui/Card";
import LocationPanel from "@/components/ui/LocationPanel";
import StatusBadge from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import { useIsMobile } from "@/components/ui/useIsMobile";
import { useLatestLocation } from "@/features/tracking/hooks/useLatestLocation";
import { tripsService } from "@/features/trips/service/tripsService";
import { formatAgo, formatDateTime, formatLongDate, formatTime, todayISO } from "@/lib/formatting/date";
import { labelOf } from "@/lib/formatting/labels";
import { toList } from "@/lib/http/page";
import LiveTripItem from "./LiveTripItem";
import styles from "./LiveLocationScreen.module.scss";

const liveTripsKey = (date) => ["trips", "list", { businessDate: date, status: "IN_PROGRESS", size: 100 }];

function SelectedTripPanel({ trip }) {
  const loc = useLatestLocation(trip.id);
  const location = loc.data;
  const state = loc.isPending ? "loading" : loc.isError && location === undefined ? "error" : undefined;

  return (
    <Card
      title={trip.routeName || `Sefer #${trip.id}`}
      subtitle={
        <span className={styles.panelHead}>
          <span className="mono">{formatTime(trip.plannedStartTime)}</span>
          <span>{labelOf("direction", trip.direction).label}</span>
        </span>
      }
      actions={
        <Link href={`/seferler/${trip.id}?tab=konum`} className="p-button p-button-secondary p-button-sm">
          Sefer detayı <i className="pi pi-chevron-right" aria-hidden="true" />
        </Link>
      }
      footer={<span className={styles.foot}>Yalnızca son konum gösterilir; tahmini varış ve iz geçmişi API&apos;de yok.</span>}
    >
      <div className="stack-lg">
        <LocationPanel location={location ?? null} state={state} tripStatus={trip.status} onRetry={loc.refetch} />
        <DefinitionList
          items={[
            ["Araç", <VehiclePlate key="p" plate={trip.vehiclePlate} />],
            ["Şoför", trip.driverFullName],
            ["Hostes", trip.hostessFullName || "Hostes yok"],
            ["Son kayıt", location ? formatDateTime(location.recordedAt) : "—"],
            ["Güncellik", location ? formatAgo(location.staleSeconds) : "—"],
            [
              "Durum",
              location ? (
                location.isOffline ? (
                  <StatusBadge key="s" tone="danger" icon="pi-wifi" label="Bağlantı yok (90 sn üzeri)" />
                ) : (
                  <StatusBadge key="s" tone="live" label="Canlı" />
                )
              ) : (
                "Konum yok"
              ),
            ],
          ]}
        />
      </div>
    </Card>
  );
}

/**
 * ADMIN "Canlı Konum" (SCREEN_MATRIX §2): bugünün yoldaki seferleri + seçili seferin son konumu.
 * Toplu konum ucu yok; her sefer için ayrı `GET /api/tracking/trips/{id}/latest` (15 sn) atılır.
 */
export default function LiveLocationScreen() {
  const today = todayISO();
  const isMobile = useIsMobile();
  const panelRef = useRef(null);
  const [selectedId, setSelectedId] = useState(null);

  const query = useQuery({
    queryKey: liveTripsKey(today),
    queryFn: () => tripsService.list({ businessDate: today, status: "IN_PROGRESS", size: 100 }),
    select: (resp) => [...toList(resp)].sort((a, b) => String(a.plannedStartTime ?? "").localeCompare(String(b.plannedStartTime ?? ""))),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });

  const trips = query.data ?? [];
  const selected = trips.find((t) => t.id === selectedId) ?? trips[0] ?? null;

  const select = (id) => {
    setSelectedId(id);
    if (isMobile && panelRef.current) panelRef.current.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <>
      <PageMeta title="Canlı Konum" subtitle={formatLongDate(today)} />
      {query.isPending && !query.data ? (
        <ScreenState kind="loading" rows={3} />
      ) : query.isError && !query.data ? (
        query.error?.status === 403 ? (
          <ScreenState kind="forbidden" homeHref="/dashboard" />
        ) : (
          <ScreenState kind="error" error={query.error} onRetry={query.refetch} />
        )
      ) : trips.length === 0 ? (
        <ScreenState
          kind="empty"
          icon="pi-map-marker"
          title="Yoldaki sefer yok"
          message="Bugün başlatılmış ve devam eden sefer olmadığında liste boş kalır. Bir sefer başladığında burada görünür."
          action={
            <Link href="/dashboard" className="p-button p-button-secondary">
              Günlük Operasyon
            </Link>
          }
        />
      ) : (
        <div className={styles.split}>
          <Card title="Yoldaki seferler" subtitle={`${trips.length} sefer`} padded={false} footer={<span className={styles.foot}>Liste 30 sn, konumlar 15 sn&apos;de bir yenilenir.</span>}>
            <div className={styles.list}>
              {trips.map((t) => (
                <LiveTripItem key={t.id} trip={t} selected={selected?.id === t.id} onSelect={select} />
              ))}
            </div>
          </Card>
          <div ref={panelRef}>{selected && <SelectedTripPanel trip={selected} />}</div>
        </div>
      )}
    </>
  );
}
