"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "primereact/button";
import Card from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import LocationPanel from "@/components/ui/LocationPanel";
import { useLatestLocation } from "@/features/tracking/hooks/useLatestLocation";
import { formatClock } from "@/lib/formatting/date";
import { errorMessage } from "@/lib/http/apiError";
import { useSendLocation } from "@/features/trips/action/useTripActions";
import styles from "@/features/trips/style/trips.module.scss";

const SHARE_INTERVAL_MS = 15_000;

function readPosition() {
  return new Promise((resolve, reject) => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      reject({ code: "UNSUPPORTED" });
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: true, timeout: 12_000, maximumAge: 5_000 });
  });
}

/** DRIVER konum paylaşımı: tarayıcı Geolocation → POST /api/tracking/trips/{id}/locations. */
function LocationShare({ trip }) {
  const send = useSendLocation(trip.id);
  const [sharing, setSharing] = useState(false);
  const [problem, setProblem] = useState(null); // { tone, title, text }
  const [lastSent, setLastSent] = useState(null);
  const timer = useRef(null);
  const busy = useRef(false);

  const stop = () => {
    if (timer.current) clearInterval(timer.current);
    timer.current = null;
    setSharing(false);
  };

  useEffect(() => () => timer.current && clearInterval(timer.current), []);

  const sendOnce = async () => {
    if (busy.current) return false;
    busy.current = true;
    try {
      const pos = await readPosition();
      const recordedAt = new Date().toISOString();
      await send.mutateAsync({ lat: pos.coords.latitude, lng: pos.coords.longitude, recordedAt });
      setLastSent(recordedAt);
      setProblem(null);
      return true;
    } catch (err) {
      if (err?.code === 1) {
        setProblem({ tone: "danger", title: "Konum izni reddedildi", text: "Tarayıcı ayarlarından bu site için konum iznini açın, sonra tekrar deneyin." });
        stop();
      } else if (err?.code === "UNSUPPORTED") {
        setProblem({ tone: "danger", title: "Konum desteklenmiyor", text: "Bu cihaz veya tarayıcı konum paylaşımını desteklemiyor." });
        stop();
      } else if (err?.code === 2 || err?.code === 3) {
        setProblem({ tone: "warn", title: "Konum alınamadı", text: "GPS sinyali zayıf olabilir. Paylaşım açıksa tekrar denenecek." });
      } else {
        setProblem({ tone: "danger", title: "Konum gönderilemedi", text: errorMessage(err, "Konum gönderilemedi.") });
        if (err?.status === 409 || err?.status === 404 || err?.status === 403) stop();
      }
      return false;
    } finally {
      busy.current = false;
    }
  };

  const start = async () => {
    const ok = await sendOnce();
    if (!ok) return;
    setSharing(true);
    if (timer.current) clearInterval(timer.current);
    timer.current = setInterval(sendOnce, SHARE_INTERVAL_MS);
  };

  return (
    <Card
      title="Konum paylaşımı"
      subtitle="Paylaşım açıkken konum 15 sn'de bir gönderilir. Uygulama açık kalmalıdır."
      actions={
        sharing ? <StatusBadge tone="live" label="Açık" /> : <StatusBadge tone="muted" icon="pi-wifi" label="Kapalı" />
      }
    >
      <div className="stack">
        {problem && (
          <Notice tone={problem.tone} title={problem.title}>
            {problem.text}
          </Notice>
        )}
        <div className={styles.shareRow}>
          {sharing ? (
            <Button type="button" icon="pi pi-stop-circle" label="Paylaşımı durdur" className="p-button-secondary" onClick={stop} />
          ) : (
            <Button type="button" icon="pi pi-wifi" label="Paylaşımı başlat" onClick={start} loading={send.isPending && !sharing} />
          )}
          <Button
            type="button"
            icon="pi pi-send"
            label="Şimdi gönder"
            outlined
            onClick={sendOnce}
            loading={send.isPending && sharing}
          />
        </div>
        <span className={styles.meta}>{lastSent ? `Son gönderim: ${formatClock(lastSent)}` : "Henüz konum gönderilmedi."}</span>
      </div>
    </Card>
  );
}

/**
 * Saha sefer ekranı → Konum sekmesi. mode: driver | hostess. DRIVER paylaşır, HOSTESS yalnız görür. ETA ve iz geçmişi yoktur.
 * D/H için sefer yolda değilse latest 404 döner (API ref §8.2) → panel boş durum gösterir.
 */
export default function LocationTab({ trip, mode }) {
  const inProgress = trip.status === "IN_PROGRESS";
  const canShare = mode === "driver";
  const latest = useLatestLocation(trip.id, { enabled: inProgress, live: inProgress });
  const state = latest.isPending && latest.fetchStatus !== "idle" ? "loading" : latest.isError ? "error" : undefined;

  return (
    <div className="stack-lg">
      <LocationPanel location={latest.data ?? null} tripStatus={trip.status} state={state} onRetry={latest.refetch} />
      {canShare ? (
        inProgress ? (
          <LocationShare trip={trip} />
        ) : (
          <Notice tone="muted" title={trip.status === "PLANNED" ? "Sefer başlamadı" : "Sefer aktif değil"}>
            Konum yalnızca yoldaki seferde paylaşılır.
          </Notice>
        )
      ) : (
        <Notice tone="muted">Hostes rolü konumu yalnızca görüntüler; konumu şoför cihazı gönderir.</Notice>
      )}
    </div>
  );
}
