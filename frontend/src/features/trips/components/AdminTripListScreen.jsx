"use client";

import { Suspense, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import Field from "@/components/ui/Field";
import Card from "@/components/ui/Card";
import StatusBadge from "@/components/ui/StatusBadge";
import VehiclePlate from "@/components/ui/VehiclePlate";
import { useIsMobile } from "@/components/ui/useIsMobile";
import { labelOf, optionsOf } from "@/lib/formatting/labels";
import { formatDate, formatTime, todayISO } from "@/lib/formatting/date";
import { pageMeta, toList } from "@/lib/http/page";
import { useTripList } from "@/features/trips/hooks/useTripQueries";
import { TripCard } from "./TripParts";
import { TripCreateDialog } from "./TripFormDialogs";
import styles from "@/features/trips/style/trips.module.scss";

const PAGE_SIZE = 20;
const STATUS_OPTIONS = [{ value: "", label: "Tümü" }, ...optionsOf("tripStatus")];

function TripTable({ trips, onOpen }) {
  return (
    <div className={styles.tableWrap}>
    <table className={styles.table}>
      <thead>
        <tr>
          <th scope="col">Saat</th>
          <th scope="col">Güzergâh</th>
          <th scope="col">Araç</th>
          <th scope="col">Şoför / hostes</th>
          <th scope="col">Durum</th>
          <th scope="col">
            <span className="sr-only">Detay</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {trips.map((t) => (
          <tr key={t.id} onClick={() => onOpen(t)}>
            <td>
              <span className={`${styles.time} mono`} style={{ fontSize: 17 }}>
                {formatTime(t.plannedStartTime)}
              </span>
              <div className={styles.meta}>{formatDate(t.businessDate)}</div>
            </td>
            <td>
              <b>{t.routeName}</b>
              <div className={styles.meta}>{labelOf("direction", t.direction).label}</div>
            </td>
            <td>
              <VehiclePlate plate={t.vehiclePlate} />
            </td>
            <td>
              {t.driverFullName || "—"}
              <div className={styles.meta}>{t.hostessFullName || "Hostes yok"}</div>
            </td>
            <td>
              <StatusBadge group="tripStatus" value={t.status} />
            </td>
            <td style={{ textAlign: "right" }}>
              <a
                href={`/seferler/${t.id}`}
                className="p-button p-button-text p-button-sm"
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  onOpen(t);
                }}
              >
                Detay <i className="pi pi-chevron-right" aria-hidden="true" style={{ marginLeft: 4 }} />
              </a>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
    </div>
  );
}

function AdminTripList() {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const isMobile = useIsMobile();
  const [date, setDate] = useState(() => search.get("tarih") || todayISO());
  const [status, setStatus] = useState(() => search.get("durum") || "");
  const [page, setPage] = useState(0);
  const createOpen = search.get("yeni") === "1";

  const params = { businessDate: date || undefined, status: status || undefined, page, size: PAGE_SIZE, sort: "businessDate,desc" };
  const query = useTripList(params);

  const setCreateOpen = (open) => {
    const sp = new URLSearchParams(search.toString());
    if (open) sp.set("yeni", "1");
    else sp.delete("yeni");
    const qs = sp.toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  };

  const openTrip = (t) => router.push(`/seferler/${t.id}`);
  const meta = query.data ? pageMeta(query.data) : null;

  return (
    <>
      <PageMeta title="Seferler" crumb="Operasyon" subtitle={date ? formatDate(date) : "Tüm tarihler"} />
      <Card padded={false} aria-label="Sefer listesi">
        <div className={styles.filters}>
          <Field label="Tarih">
            <InputText
              type="date"
              value={date}
              onChange={(e) => {
                setDate(e.target.value);
                setPage(0);
              }}
            />
          </Field>
          <Field label="Durum" htmlFor="trip-status-filter">
            <Dropdown
              inputId="trip-status-filter"
              value={status}
              options={STATUS_OPTIONS}
              onChange={(e) => {
                setStatus(e.value ?? "");
                setPage(0);
              }}
            />
          </Field>
          <div className={styles.filterAction}>
            <Button type="button" icon="pi pi-plus" label="Yeni sefer" onClick={() => setCreateOpen(true)} />
          </div>
        </div>
        <QueryState
          query={query}
          isEmpty={(d) => toList(d).length === 0}
          empty={
            <ScreenState
              kind="empty"
              icon="pi-car"
              title="Bu filtrede sefer yok"
              message="Filtreleri değiştirin veya yeni sefer oluşturun."
              bare
              action={<Button type="button" icon="pi pi-plus" label="Yeni sefer" outlined onClick={() => setCreateOpen(true)} />}
            />
          }
        >
          {(data) =>
            isMobile ? (
              <div className={styles.cardList}>
                {toList(data).map((t) => (
                  <TripCard key={t.id} trip={t} href={`/seferler/${t.id}`} />
                ))}
              </div>
            ) : (
              <TripTable trips={toList(data)} onOpen={openTrip} />
            )
          }
        </QueryState>
        {meta && meta.totalPages > 1 && (
          <div className={styles.pager}>
            <span className={styles.meta}>
              Sayfa {meta.page + 1} / {meta.totalPages} · {meta.totalElements} sefer
            </span>
            <div className="row">
              <Button
                type="button"
                icon="pi pi-chevron-left"
                label="Önceki"
                className="p-button-secondary"
                disabled={page === 0 || query.isFetching}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              />
              <Button
                type="button"
                icon="pi pi-chevron-right"
                iconPos="right"
                label="Sonraki"
                className="p-button-secondary"
                disabled={page + 1 >= meta.totalPages || query.isFetching}
                onClick={() => setPage((p) => p + 1)}
              />
            </div>
          </div>
        )}
      </Card>

      {createOpen && (
        <TripCreateDialog
          visible
          defaultDate={date}
          onHide={() => setCreateOpen(false)}
          onCreated={(trip) => router.replace(`/seferler/${trip.id}`)}
        />
      )}
    </>
  );
}

/** ADMIN /seferler: tarih + durum filtresi, sayfalı liste (mobilde kart), "Yeni sefer" dialogu (`?yeni=1`). */
export default function AdminTripListScreen() {
  return (
    <Suspense fallback={<ScreenState kind="loading" />}>
      <AdminTripList />
    </Suspense>
  );
}
