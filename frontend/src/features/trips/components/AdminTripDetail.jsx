"use client";

import { useState } from "react";
import { Button } from "primereact/button";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import ReasonDialog from "@/components/feedback/ReasonDialog";
import { confirmAction } from "@/components/feedback/feedback";
import Card, { DefinitionList } from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import StatusBadge, { NoticeBadge } from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import LocationPanel from "@/components/ui/LocationPanel";
import { useLatestLocation } from "@/features/tracking/hooks/useLatestLocation";
import { labelOf } from "@/lib/formatting/labels";
import { formatAgo, formatClock, formatDate, formatDateTime, formatRelativeDateTime, formatTime } from "@/lib/formatting/date";
import { groupRosterByStop, isTripOpen, rosterCounts } from "@/features/trips/lib/fieldUtils";
import { useAbsenceNotices, useIncidents, useRoster, useRouteStops, useTrip } from "@/features/trips/hooks/useTripQueries";
import { useTabParam } from "@/features/trips/hooks/useTabParam";
import { useCancelTrip, useResolveIncident, useTripTransition } from "@/features/trips/action/useTripActions";
import { DirectionBadge, IncidentList, StopTimeline, TabBar } from "./TripParts";
import RosterList from "./RosterList";
import IncidentReportDialog from "./IncidentReportDialog";
import { TripEditDialog } from "./TripFormDialogs";
import { TripNotFound } from "./FieldTripDetail";
import styles from "@/features/trips/style/trips.module.scss";

const TABS = ["ozet", "yolcular", "olaylar", "konum", "bildirimler"];

function AdminTripHeader({ trip, counts, onEdit, onCancel, onShowIncidents }) {
  const transition = useTripTransition(trip.id);
  const open = isTripOpen(trip);

  const run = async (kind) => {
    const ok = await confirmAction(
      kind === "start"
        ? { title: "Seferi başlat", message: "Sefer “Yolda” durumuna geçer.", acceptLabel: "Başlat", tone: "info" }
        : {
            title: "Seferi tamamla",
            message: counts.NONE ? `${counts.NONE} öğrenci için yoklama girilmedi. Yine de tamamlansın mı?` : "Sefer tamamlanır.",
            acceptLabel: "Tamamla",
            tone: counts.NONE ? "danger" : "info",
          }
    );
    if (ok) transition.mutate(kind);
  };

  return (
    <Card>
      <div className="stack-lg">
        <div className="between" style={{ alignItems: "flex-start" }}>
          <div className="stack" style={{ minWidth: 0 }}>
            <div className="row">
              <StatusBadge group="tripStatus" value={trip.status} />
              <DirectionBadge direction={trip.direction} long />
              {trip.openCancellationRequestId && <StatusBadge tone="danger" icon="pi-ban" label="Açık iptal talebi" />}
            </div>
            <h2 className={styles.headTitle}>{trip.routeName}</h2>
            <div className={`row ${styles.meta}`}>
              <span>{formatDate(trip.businessDate)}</span>
              <span>· Planlanan {formatTime(trip.plannedStartTime)}</span>
              {trip.startedAt && <span>· Başladı {formatClock(trip.startedAt)}</span>}
              {trip.completedAt && <span>· Bitti {formatClock(trip.completedAt)}</span>}
            </div>
          </div>
          <div className="row">
            {trip.status === "PLANNED" && (
              <>
                <Button type="button" icon="pi pi-pencil" label="Düzenle" className="p-button-secondary" onClick={onEdit} />
                <Button type="button" icon="pi pi-play" label="Başlat" loading={transition.isPending} onClick={() => run("start")} />
              </>
            )}
            {trip.status === "IN_PROGRESS" && (
              <Button type="button" icon="pi pi-flag" label="Tamamla" loading={transition.isPending} onClick={() => run("complete")} />
            )}
            {open && (
              <Button
                type="button"
                icon="pi pi-ban"
                label="Seferi iptal et"
                severity="danger"
                outlined
                className={styles.cancelBtn}
                onClick={onCancel}
              />
            )}
          </div>
        </div>
        <div className={styles.crew}>
          <VehiclePlate plate={trip.vehiclePlate} />
          <span>
            <i className="pi pi-user" aria-hidden="true" />
            Şoför: {trip.driverFullName || "—"}
          </span>
          <span>
            <i className="pi pi-users" aria-hidden="true" />
            Hostes: {trip.hostessFullName || "Hostes atanmadı"}
          </span>
        </div>
        {trip.status === "CANCELLED" && (
          <Notice tone="danger" icon="pi-ban" title="Sefer iptal edildi">
            {trip.cancellationReason || "Gerekçe girilmedi."}
          </Notice>
        )}
        {trip.openCancellationRequestId && (
          <Notice
            tone="warn"
            title="Personel iptal talebi açtı"
            action={<Button type="button" label="Talebi incele" text size="small" onClick={onShowIncidents} />}
          >
            Karar verene kadar sefer planlandığı gibi devam eder.
          </Notice>
        )}
      </div>
    </Card>
  );
}

function SummaryTab({ trip, rows, counts, noticesCount, onGo }) {
  const stops = useRouteStops(trip.routeId);
  const latest = useLatestLocation(trip.id, { enabled: trip.status === "IN_PROGRESS" });
  return (
    <div className="grid-main">
      <Card title="Durak sırası">
        <QueryState query={stops} loadingRows={3}>
          {(data) => <StopTimeline stops={data} groups={groupRosterByStop(rows)} tripStatus={trip.status} />}
        </QueryState>
      </Card>
      <div className="stack-lg">
        <Card title="Yoklama özeti">
          <div className="stack">
            <div className={styles.summaryGrid}>
              {["BOARDED", "DROPPED_OFF", "ABSENT", "NONE"].map((s) => (
                <div className={styles.summaryCell} key={s}>
                  <StatusBadge group="attendance" value={s} />
                  <b>{counts[s]}</b>
                </div>
              ))}
            </div>
            <div className="row">
              <NoticeBadge label={`${noticesCount} veli bildirimi`} />
              <Button type="button" label="Yolcu listesine git" text size="small" onClick={() => onGo("yolcular")} />
            </div>
          </div>
        </Card>
        <Card title="Son konum">
          {trip.status !== "IN_PROGRESS" ? (
            <p className={styles.meta} style={{ margin: 0 }}>
              {trip.status === "PLANNED" ? "Sefer başlamadı; konum sefer başladıktan sonra görünür." : "Sefer aktif değil."}
            </p>
          ) : latest.data ? (
            <div className="stack">
              <div className="row">
                {latest.data.isOffline ? (
                  <StatusBadge tone="danger" icon="pi-wifi" label="Bağlantı yok" />
                ) : (
                  <StatusBadge tone="live" label="Canlı" />
                )}
                <b className="mono">Son konum {formatClock(latest.data.recordedAt)}</b>
              </div>
              <span className={styles.meta}>Güncellendi: {formatAgo(latest.data.staleSeconds)}</span>
              <Button type="button" label="Canlı konum sekmesi" text size="small" onClick={() => onGo("konum")} />
            </div>
          ) : latest.isPending ? (
            <span className={styles.meta}>Yükleniyor…</span>
          ) : (
            <Notice tone="warn" icon="pi-wifi" title="Konum yok">
              Sefer başladı ancak şoför henüz konum göndermedi.
            </Notice>
          )}
        </Card>
      </div>
    </div>
  );
}

function AdminLocationTab({ trip }) {
  const live = trip.status === "IN_PROGRESS";
  const latest = useLatestLocation(trip.id, { live });
  const l = latest.data;
  let state;
  if (trip.status === "PLANNED") state = "notStarted";
  else if (!live) state = "inactive";
  else if (latest.isPending) state = "loading";
  else if (latest.isError) state = "error";
  return (
    <div className="grid-main">
      <LocationPanel location={l ?? null} tripStatus={trip.status} state={state} onRetry={latest.refetch} />
      <Card title="Konum bilgisi">
        <DefinitionList
          items={[
            ["Son kayıt", l ? formatDateTime(l.recordedAt) : "—"],
            ["Güncellik", l ? formatAgo(l.staleSeconds) : "—"],
            ["Durum", !live ? labelOf("tripStatus", trip.status).label : l ? (l.isOffline ? "Bağlantı yok (90 sn üzeri)" : "Canlı") : "Konum yok"],
          ]}
        />
        <p className={styles.meta} style={{ marginBottom: 0 }}>
          Yalnızca son konum gösterilir; tahmini varış ve geçmiş iz bu sürümde yoktur.
        </p>
      </Card>
    </div>
  );
}

function NoticesTab({ trip, rows }) {
  const notices = useAbsenceNotices(trip.id);
  const statusOf = (studentId) => rows.find((r) => r.studentId === studentId)?.status ?? "NONE";
  const stopOf = (studentId) => rows.find((r) => r.studentId === studentId)?.stopName;
  return (
    <Card title="Veli “servise binmeyecek” bildirimleri" padded={false} footer={<span className={styles.meta}>Bildirim resmi yoklama değildir; yoklama sütunu görevlinin kaydını gösterir.</span>}>
      <QueryState
        query={notices}
        isEmpty={(d) => d.length === 0}
        empty={<ScreenState kind="empty" icon="pi-flag" title="Aktif veli bildirimi yok" message="Veliler sefer kapanana kadar bildirim gönderebilir." bare compact />}
      >
        {(data) => (
          <div className={styles.list}>
            {data.map((n) => (
              <div className={styles.li} key={n.id}>
                <span className={styles.liIcon} aria-hidden="true">
                  <i className="pi pi-flag" />
                </span>
                <div className={`${styles.grow} stack`}>
                  <div className="between">
                    <b>{n.studentFullName}</b>
                    <StatusBadge group="attendance" value={statusOf(n.studentId)} />
                  </div>
                  <div className={styles.meta}>
                    {stopOf(n.studentId) ? `${stopOf(n.studentId)} · ` : ""}
                    {n.parentFullName} · {formatRelativeDateTime(n.createdAt)}
                  </div>
                  <div className={styles.quote}>{n.note || "Not eklenmemiş."}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </QueryState>
    </Card>
  );
}

function AdminTripBody({ trip }) {
  const [tab, setTab] = useTabParam(TABS, "ozet");
  const [editOpen, setEditOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [incidentOpen, setIncidentOpen] = useState(0);
  const [resolve, setResolve] = useState(null); // { incident, approve }
  const open = isTripOpen(trip);
  const roster = useRoster(trip.id);
  const incidents = useIncidents(trip.id, { live: open });
  const notices = useAbsenceNotices(trip.id);
  const cancel = useCancelTrip(trip.id);
  const resolveMut = useResolveIncident(trip.id);
  const rows = roster.data ?? [];
  const counts = rosterCounts(rows);
  const noticesCount = notices.data?.length ?? counts.notices;

  const tabs = [
    { key: "ozet", label: "Özet" },
    { key: "yolcular", label: "Yolcular", count: roster.data ? counts.total : undefined },
    { key: "olaylar", label: "Olaylar", count: incidents.data?.length },
    { key: "konum", label: "Canlı konum" },
    { key: "bildirimler", label: "Veli bildirimleri", count: notices.data?.length },
  ];

  return (
    <>
      <PageMeta title={`Sefer #${trip.id}`} crumb="Operasyon / Seferler" subtitle={`${trip.routeName} · ${formatDate(trip.businessDate)}`} back="/seferler" />
      <AdminTripHeader
        trip={trip}
        counts={counts}
        onEdit={() => setEditOpen(true)}
        onCancel={() => setCancelOpen(true)}
        onShowIncidents={() => setTab("olaylar")}
      />
      <TabBar tabs={tabs} current={tab} onChange={setTab} />

      {tab === "ozet" && <SummaryTab trip={trip} rows={rows} counts={counts} noticesCount={noticesCount} onGo={setTab} />}

      {tab === "yolcular" && (
        <Card title="Yolcu listesi" actions={<span className="row"><StatusBadge group="attendance" value="NONE" /><NoticeBadge label="Veli bildirimi" /></span>}>
          <div className="stack">
            {trip.status !== "IN_PROGRESS" && (
              <Notice tone="muted">Yoklama yalnızca “Yolda” durumundaki seferde girilir. Tamamlanan seferde değişiklik düzeltme talebiyle yapılır.</Notice>
            )}
            <QueryState query={roster}>{(data) => <RosterList tripId={trip.id} tripStatus={trip.status} rows={data} mode="admin" />}</QueryState>
          </div>
        </Card>
      )}

      {tab === "olaylar" && (
        <Card
          title="Operasyon kayıtları"
          padded={false}
          actions={open && <Button type="button" icon="pi pi-plus" label="Kayıt ekle" className="p-button-secondary" size="small" onClick={() => setIncidentOpen((n) => n + 1)} />}
        >
          <QueryState query={incidents} loadingRows={2}>
            {(data) => (
              <IncidentList
                incidents={data}
                renderActions={(i) =>
                  i.type === "CANCELLATION_REQUEST" &&
                  i.status === "OPEN" && (
                    <div className="row" style={{ marginTop: 6 }}>
                      <Button
                        type="button"
                        icon="pi pi-ban"
                        label="Onayla ve seferi iptal et"
                        severity="danger"
                        size="small"
                        onClick={() => setResolve({ incident: i, approve: true })}
                      />
                      <Button
                        type="button"
                        icon="pi pi-times"
                        label="Reddet"
                        className="p-button-secondary"
                        size="small"
                        onClick={() => setResolve({ incident: i, approve: false })}
                      />
                    </div>
                  )
                }
              />
            )}
          </QueryState>
        </Card>
      )}

      {tab === "konum" && <AdminLocationTab trip={trip} />}
      {tab === "bildirimler" && <NoticesTab trip={trip} rows={rows} />}

      {editOpen && <TripEditDialog trip={trip} visible onHide={() => setEditOpen(false)} />}
      {incidentOpen > 0 && <IncidentReportDialog key={incidentOpen} tripId={trip.id} visible title="Kayıt ekle" onHide={() => setIncidentOpen(0)} />}

      <ReasonDialog
        visible={cancelOpen}
        onHide={() => setCancelOpen(false)}
        title="Seferi iptal et"
        subtitle={`${trip.routeName} · ${formatDate(trip.businessDate)} ${formatTime(trip.plannedStartTime)}`}
        icon="pi-ban"
        label="İptal gerekçesi"
        placeholder="Örn. okul erken kapanıyor"
        confirmLabel="Seferi iptal et"
        warning={{
          tone: "danger",
          title: "Bu işlem geri alınamaz",
          text: `${counts.total} öğrenci, velileri, şoför ve hostese iptal bildirimi gönderilir.${
            trip.openCancellationRequestId ? " Bu sefer için açık bir personel iptal talebi de var." : ""
          }`,
        }}
        submitting={cancel.isPending}
        onConfirm={async (reason) => {
          try {
            await cancel.mutateAsync(reason);
            setCancelOpen(false);
          } catch {
            // toast mutasyonda
          }
        }}
      />

      <ReasonDialog
        visible={Boolean(resolve)}
        onHide={() => setResolve(null)}
        title={resolve?.approve ? "İptal talebini onayla" : "İptal talebini reddet"}
        subtitle={resolve ? `${resolve.incident.reportedByFullName} · ${formatRelativeDateTime(resolve.incident.createdAt)}` : undefined}
        icon={resolve?.approve ? "pi-ban" : "pi-times"}
        tone={resolve?.approve ? "danger" : "warn"}
        label="Karar notu"
        placeholder={resolve?.approve ? "Örn. yedek araç bulunamadı" : "Örn. yedek araç ayarlandı, sefer devam edecek"}
        confirmLabel={resolve?.approve ? "Onayla ve iptal et" : "Reddet"}
        confirmSeverity={resolve?.approve ? "danger" : undefined}
        warning={
          resolve?.approve
            ? { tone: "danger", title: "Sefer iptal edilir", text: "Onay seferi iptal eder; yolcular ve görevliler bilgilendirilir. Geri alınamaz." }
            : { tone: "muted", text: "Talep reddedilir, sefer planlandığı gibi devam eder. Talep sahibine bildirim gider." }
        }
        submitting={resolveMut.isPending}
        onConfirm={async (note) => {
          try {
            await resolveMut.mutateAsync({ incidentId: resolve.incident.id, approve: resolve.approve, note });
            setResolve(null);
          } catch {
            // toast mutasyonda
          }
        }}
      >
        {resolve && (
          <Notice tone="muted" title="Talep">
            {resolve.incident.note}
          </Notice>
        )}
      </ReasonDialog>
    </>
  );
}

/** ADMIN sefer detayı. Sefer + yolcu listesi 15 sn'de bir yenilenir. */
export default function AdminTripDetail({ id }) {
  const trip = useTrip(id);
  return (
    <QueryState query={trip} notFound={<TripNotFound />}>
      {(data) => <AdminTripBody trip={data} />}
    </QueryState>
  );
}
