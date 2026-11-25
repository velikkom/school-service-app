"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "primereact/button";
import { Paginator } from "primereact/paginator";
import { SelectButton } from "primereact/selectbutton";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import Card from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import { useAuth } from "@/providers/AuthProvider";
import { formatRelativeDateTime } from "@/lib/formatting/date";
import { NOTIFICATION_PAGE_SIZE, UNREAD_WINDOW, useNotificationList } from "../hooks/useNotificationList";
import { useUnreadCount } from "../hooks/useUnreadCount";
import { useMarkRead } from "../action/useMarkRead";
import styles from "../style/notifications.module.scss";

function NotificationRow({ n, onRead, busy }) {
  const content = (
    <>
      <span className={styles.icon} aria-hidden="true">
        <i className="pi pi-bell" />
      </span>
      <span className={styles.body}>
        <span className={styles.title}>{n.title}</span>
        <span className={styles.text}>{n.body}</span>
        <span className={styles.timeMobile}>{formatRelativeDateTime(n.createdAt)}</span>
        {!n.read && <span className={styles.dot}>Okunmadı · okundu işaretlemek için dokunun</span>}
      </span>
      <span className={styles.time}>{formatRelativeDateTime(n.createdAt)}</span>
    </>
  );
  if (n.read) return <div className={styles.item}>{content}</div>;
  return (
    <button type="button" className={`${styles.item} ${styles.unread}`} onClick={() => onRead(n.id)} disabled={busy} aria-label={`${n.title} — okundu işaretle`}>
      {content}
    </button>
  );
}

/** Bildirimler (tüm roller): Tümü / Okunmamış, yalnız uygulama içi kanal, okundu işaretleme. */
export default function NotificationsScreen() {
  const { role } = useAuth();
  const [filter, setFilter] = useState("all");
  const [page, setPage] = useState(0);
  const query = useNotificationList({ filter, page });
  const unreadCount = useUnreadCount();
  const { markOne, markMany } = useMarkRead();

  const items = query.data?.items ?? [];
  const meta = query.data?.meta;
  const unreadOnPage = items.filter((n) => !n.read).map((n) => n.id);
  const unreadLabel = unreadCount.data ? `Okunmamış (${unreadCount.data})` : "Okunmamış";

  const options = [
    { value: "all", label: "Tümü" },
    { value: "unread", label: unreadLabel },
  ];

  return (
    <>
      <PageMeta title="Bildirimler" />
      <div className={`stack-lg ${styles.wrap}`}>
        {role === "ADMIN" && (
          <Notice tone="muted" icon="pi-megaphone">
            Toplu duyuru göndermek için <Link href="/bildirimler/toplu">Toplu Bildirim</Link> ekranını kullanın.
          </Notice>
        )}
        <Card
          padded={false}
          footer={
            filter === "all" && meta && meta.totalPages > 1 ? (
              <div className={styles.pager}>
                <Paginator
                  first={page * NOTIFICATION_PAGE_SIZE}
                  rows={NOTIFICATION_PAGE_SIZE}
                  totalRecords={meta.totalElements}
                  onPageChange={(e) => setPage(e.page)}
                  template="PrevPageLink CurrentPageReport NextPageLink"
                  currentPageReportTemplate="{currentPage} / {totalPages}"
                />
              </div>
            ) : filter === "unread" ? (
              <span className="text-xs muted">Son {UNREAD_WINDOW} bildirim içindeki okunmamışlar gösterilir.</span>
            ) : null
          }
        >
          <div className={styles.head}>
            <SelectButton
              className={styles.segment}
              value={filter}
              options={options}
              onChange={(e) => {
                if (!e.value) return;
                setFilter(e.value);
                setPage(0);
              }}
              allowEmpty={false}
              aria-label="Bildirim filtresi"
            />
            <Button
              type="button"
              label="Buradakileri okundu işaretle"
              icon="pi pi-check"
              size="small"
              text
              disabled={unreadOnPage.length === 0 || markMany.isPending}
              loading={markMany.isPending}
              onClick={() => markMany.mutate(unreadOnPage)}
            />
          </div>
          {query.isPending && !query.data ? (
            <ScreenState kind="loading" rows={4} bare />
          ) : query.isError && !query.data ? (
            <ScreenState kind="error" error={query.error} onRetry={query.refetch} bare />
          ) : items.length === 0 ? (
            <ScreenState
              kind="empty"
              icon="pi-bell"
              title={filter === "unread" ? "Okunmamış bildirim yok" : "Bildiriminiz yok"}
              message={filter === "unread" ? "Yeni bildirimler burada görünür." : "Sefer, talep ve ödeme gelişmeleri burada listelenir."}
              bare
            />
          ) : (
            <ul className={styles.list} aria-busy={query.isFetching}>
              {items.map((n) => (
                <li key={n.id}>
                  <NotificationRow n={n} busy={markOne.isPending && markOne.variables === n.id} onRead={(id) => markOne.mutate(id)} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
