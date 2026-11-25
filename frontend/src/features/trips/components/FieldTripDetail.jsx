"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "primereact/button";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import ReasonDialog from "@/components/feedback/ReasonDialog";
import { confirmAction, toastError } from "@/components/feedback/feedback";
import { errorMessage } from "@/lib/http/apiError";
import Card from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import { labelOf } from "@/lib/formatting/labels";
import { formatTime } from "@/lib/formatting/date";
import { groupRosterByStop, isTripOpen, rosterCounts } from "@/features/trips/lib/fieldUtils";
import { useIncidents, useRoster, useRouteStops, useTrip } from "@/features/trips/hooks/useTripQueries";
import { useTabParam } from "@/features/trips/hooks/useTabParam";
import { useCreateIncident, useTripTransition } from "@/features/trips/action/useTripActions";
import { DirectionBadge, IncidentList, StopTimeline, TabBar } from "./TripParts";
import RosterList from "./RosterList";
import LocationTab from "./LocationTab";
import IncidentReportDialog from "./IncidentReportDialog";
import styles from "@/features/trips/style/trips.module.scss";

const TABS = ["yoklama", "duraklar", "konum", "olaylar"];

export function TripNotFound() {
  return (
    <ScreenState
      kind="notFound"
      title="Sefer bulunamadı"
      message="Bu sefer yok ya da size atanmış değil."
      action={
        <Link href="/seferler" className="p-button p-button-secondary">
          Seferlere dön
        </Link>
      }
    />
  );
}

/** Saha sefer başlığı: durum, yön/saat, plaka, güzergâh ve bağlama göre uyarılar. */
function FieldTripHeader({ trip, counts }) {
  return (
    <Card>
      <div className="stack">
        <div className="between">
          <div className="row">
            <StatusBadge group="tripStatus" value={trip.status} />
            <DirectionBadge direction={trip.direction} time={formatTime(trip.plannedStartTime)} />
          </div>
          <VehiclePlate plate={trip.vehiclePlate} />
        </div>
        <h2 className={styles.headTitle} style={{ fontSize: 18 }}>
          {trip.routeName}
        </h2>
        {trip.status === "PLANNED" && (
          <Notice tone="info" icon="pi-clock" title="Sefer başlamadı">
            Yoklama ve konum paylaşımı sefer başlatılınca açılır.
            {counts.notices ? ` Şimdiden ${counts.notices} veli bildirimi var.` : ""}
          </Notice>
        )}
        {trip.status === "CANCELLED" && (
          <Notice tone="danger" icon="pi-ban" title="Sefer iptal edildi">
            {trip.cancellationReason || "Gerekçe girilmedi."}
          </Notice>
        )}
        {trip.status === "COMPLETED" && (
          <Notice tone="muted" title="Sefer tamamlandı">
            Yanlış girilen yoklama için öğrenci satırından düzeltme talebi açabilirsiniz.
          </Notice>
        )}
        {trip.openCancellationRequestId && (
          <Notice tone="warn" icon="pi-clock" title="İptal talebiniz yönetici kararını bekliyor">
            Karar verilene kadar sefer planlandığı gibi devam eder.
          </Notice>
        )}
      </div>
    </Card>
  );
}

/** Yapışkan alt aksiyon çubuğu: başlat/tamamla, olay bildir, iptal talebi. Doğrudan iptal YOK. */
function FieldActionBar({ trip, counts, onIncident, onCancelRequest }) {
  const transition = useTripTransition(trip.id);
  const run = async (kind) => {
    const ok = await confirmAction(
      kind === "start"
        ? { title: "Seferi başlat", message: "Sefer “Yolda” durumuna geçer; yoklama ve konum paylaşımı açılır.", acceptLabel: "Seferi başlat", tone: "info" }
        : {
            title: "Seferi tamamla",
            message: counts.NONE
              ? `${counts.NONE} öğrenci için yoklama girilmedi. Yine de seferi tamamlamak istiyor musunuz?`
              : "Sefer tamamlanır; sonrasında yoklama yalnızca düzeltme talebiyle değişir.",
            acceptLabel: "Seferi tamamla",
            tone: counts.NONE ? "danger" : "info",
          }
    );
    if (ok) transition.mutate(kind);
  };

  return (
    <div className={styles.actionBar} role="toolbar" aria-label="Sefer işlemleri">
      {trip.status === "PLANNED" ? (
        <Button type="button" icon="pi pi-play" label="Seferi başlat" className={styles.barMain} loading={transition.isPending} onClick={() => run("start")} />
      ) : (
        <Button type="button" icon="pi pi-flag" label="Seferi tamamla" className={styles.barMain} loading={transition.isPending} onClick={() => run("complete")} />
      )}
      <Button type="button" severity="warning" outlined className={styles.barSide} aria-label="Olay bildir" onClick={onIncident}>
        <i className="pi pi-exclamation-triangle" aria-hidden="true" />
        <span className={styles.barLabel} style={{ marginLeft: 8 }}>
          Olay bildir
        </span>
      </Button>
      <Button
        type="button"
        severity="danger"
        outlined
        className={styles.barSide}
        aria-label="İptal talebi"
        title={trip.openCancellationRequestId ? "Açık iptal talebiniz var" : undefined}
        disabled={Boolean(trip.openCancellationRequestId)}
        onClick={onCancelRequest}
      >
        <i className="pi pi-ban" aria-hidden="true" />
        <span className={styles.barLabel} style={{ marginLeft: 8 }}>
          İptal talebi
        </span>
      </Button>
    </div>
  );
}

function FieldTripBody({ trip, role }) {
  const [tab, setTab] = useTabParam(TABS, "yoklama");
  const [incidentOpen, setIncidentOpen] = useState(0);
  const [cancelOpen, setCancelOpen] = useState(false);
  const roster = useRoster(trip.id);
  const incidents = useIncidents(trip.id, { live: isTripOpen(trip) });
  const stops = useRouteStops(trip.routeId, { enabled: tab === "duraklar" });
  const createIncident = useCreateIncident(trip.id);
  const rows = roster.data ?? [];
  const counts = rosterCounts(rows);
  const open = isTripOpen(trip);

  const tabs = [
    { key: "yoklama", label: "Yoklama", count: roster.data ? counts.total : undefined },
    { key: "duraklar", label: "Duraklar" },
    { key: "konum", label: "Konum" },
    { key: "olaylar", label: "Olaylar", count: incidents.data ? incidents.data.length : undefined },
  ];

  return (
    <>
      <PageMeta
        title={trip.routeName}
        subtitle={`${labelOf("tripStatus", trip.status).label} · ${trip.vehiclePlate ?? "Plaka yok"}`}
        back="/seferler"
      />
      <div className={styles.narrow}>
        <FieldTripHeader trip={trip} counts={counts} />
        <TabBar tabs={tabs} current={tab} onChange={setTab} />

        {tab === "yoklama" && (
          <div className="stack">
            {counts.notices > 0 && (
              <Notice tone="notice" title={`${counts.notices} öğrenci için veli “servise binmeyecek” bildirdi`}>
                Mor işaretli satırlar. Bu bir bilgilendirmedir; resmi yoklama sizin kaydınızdır.
              </Notice>
            )}
            <QueryState query={roster}>{(data) => <RosterList tripId={trip.id} tripStatus={trip.status} rows={data} mode="field" />}</QueryState>
          </div>
        )}

        {tab === "duraklar" && (
          <Card title="Durak sırası" subtitle="Geçilen duraklar yoklamadan türetilir.">
            <QueryState query={stops} loadingRows={3}>
              {(data) => <StopTimeline stops={data} groups={groupRosterByStop(rows)} tripStatus={trip.status} />}
            </QueryState>
          </Card>
        )}

        {tab === "konum" && <LocationTab trip={trip} mode={role === "DRIVER" ? "driver" : "hostess"} />}

        {tab === "olaylar" && (
          <Card title="Operasyon kayıtları" padded={false}>
            <QueryState query={incidents} loadingRows={2}>
              {(data) => <IncidentList incidents={data} />}
            </QueryState>
          </Card>
        )}

        {open && (
          <FieldActionBar
            trip={trip}
            counts={counts}
            onIncident={() => setIncidentOpen((n) => n + 1)}
            onCancelRequest={() => setCancelOpen(true)}
          />
        )}
      </div>

      {incidentOpen > 0 && (
        <IncidentReportDialog key={incidentOpen} tripId={trip.id} visible onHide={() => setIncidentOpen(0)} />
      )}
      <ReasonDialog
        visible={cancelOpen}
        onHide={() => setCancelOpen(false)}
        title="İptal talebi"
        subtitle={`${trip.routeName} · ${formatTime(trip.plannedStartTime)}`}
        icon="pi-ban"
        tone="danger"
        label="Gerekçe"
        placeholder="Örn. araç arızalandı, yedek araç yok"
        confirmLabel="Talebi gönder"
        warning={{ tone: "warn", title: "Sefer iptal edilmez", text: "Talep yöneticiye gider. Karar verilene kadar sefer planlandığı gibi devam eder." }}
        submitting={createIncident.isPending}
        onConfirm={async (note) => {
          try {
            await createIncident.mutateAsync({ type: "CANCELLATION_REQUEST", note });
            setCancelOpen(false);
          } catch (err) {
            toastError(errorMessage(err, "İptal talebi gönderilemedi."));
          }
        }}
      />
    </>
  );
}

/** DRIVER/HOSTESS sefer ekranı. Sefer ve yolcu listesi 15 sn'de bir yenilenir. */
export default function FieldTripDetail({ id, role }) {
  const trip = useTrip(id);
  return (
    <QueryState query={trip} notFound={<TripNotFound />} forbiddenHome="/seferler">
      {(data) => <FieldTripBody trip={data} role={role} />}
    </QueryState>
  );
}
