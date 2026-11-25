"use client";

import Link from "next/link";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import ScreenState from "@/components/feedback/ScreenState";
import StatusBadge from "@/components/ui/StatusBadge";
import { useIsMobile } from "@/components/ui/useIsMobile";
import { pageMeta, toList } from "@/lib/http/page";
import styles from "./ManagementList.module.scss";

const DESKTOP_PAGER = "FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport";
const MOBILE_PAGER = "PrevPageLink CurrentPageReport NextPageLink";

/**
 * Yönetim listeleri için ortak şablon (DESIGN_SYSTEM.md §6 "Tablo / liste şablonu"):
 * filtre çubuğu (sol: filtreler, sağ: birincil aksiyon) + lazy sayfalı DataTable + sayfalayıcı.
 * < 900 px'te satırlar etiketli kartlara döner (responsiveLayout="stack").
 * Y/B/H durumları ScreenState ile; yenilemede eski veri korunur (sorgu keepPreviousData kullanmalı).
 *
 * columns: [{ key, header, body?: (row) => node, field?, className?, align?: "right" }]
 * query: TanStack sorgusu — yanıt Spring Page zarfı ({content,page,size,totalElements}) veya dizi.
 * paginated=false ise sayfalayıcı gösterilmez (düz dizi uçları).
 */
export default function ManagementList({
  title,
  subtitle,
  filters,
  actions,
  columns,
  query,
  page = 0,
  size = 20,
  onPage,
  paginated = true,
  rowActions,
  rowActionsHeader = "İşlemler",
  dataKey = "id",
  ariaLabel,
  emptyTitle = "Henüz kayıt yok",
  emptyMessage,
  emptyIcon,
  emptyAction,
  isFiltered = false,
  footnote,
  rowClassName,
  forbiddenHome = "/dashboard",
}) {
  const isMobile = useIsMobile();
  const data = query?.data;
  const rows = toList(data);
  const meta = pageMeta(data);
  const total = paginated ? meta.totalElements : rows.length;

  let body;
  if (query?.isPending && !data) {
    body = <ScreenState kind="loading" bare rows={4} title="Liste yükleniyor" />;
  } else if (query?.isError && !data) {
    const status = query.error?.status;
    if (status === 403) body = <ScreenState kind="forbidden" bare homeHref={forbiddenHome} />;
    else body = <ScreenState kind="error" bare error={query.error} onRetry={() => query.refetch()} />;
  } else if (rows.length === 0 && page === 0) {
    body = isFiltered ? (
      <ScreenState kind="empty" bare compact icon="pi-filter-slash" title="Filtreye uyan kayıt yok" message="Filtreleri değiştirip tekrar deneyin." />
    ) : (
      <ScreenState kind="empty" bare icon={emptyIcon} title={emptyTitle} message={emptyMessage} action={emptyAction} />
    );
  } else {
    body = (
      <DataTable
        value={rows}
        dataKey={dataKey}
        lazy={paginated}
        paginator={paginated && (total > size || page > 0)}
        first={page * size}
        rows={size}
        totalRecords={total}
        onPage={(e) => onPage?.({ page: e.page, size: e.rows })}
        rowsPerPageOptions={[10, 20, 50]}
        paginatorTemplate={isMobile ? MOBILE_PAGER : DESKTOP_PAGER}
        currentPageReportTemplate="{first}–{last} / {totalRecords}"
        responsiveLayout="stack"
        breakpoint="899px"
        loading={Boolean(query?.isFetching && query?.isPlaceholderData)}
        aria-label={ariaLabel || title}
        className={styles.table}
        rowClassName={rowClassName}
        emptyMessage="Kayıt yok"
        // Sprint 3 düzeltmesi: PrimeReact hücreleri varsayılan olarak memo'lanır ve yalnız satır verisi değişince
        // yeniden çizilir. Hücreler ayrı yüklenen sözlüklere (ör. güzergâh/öğrenci adı) baktığı için sözlük
        // satırlardan sonra gelirse "Güzergâh #3" gibi geçici etiket kalıcı oluyordu. Listeler ≤100 satır.
        cellMemo={false}
      >
        {columns.map((c) => (
          <Column
            key={c.key ?? c.field}
            field={c.field}
            header={c.header}
            body={c.body}
            className={[c.className, c.align === "right" ? styles.right : "", c.main ? styles.main : ""].filter(Boolean).join(" ") || undefined}
            headerClassName={c.align === "right" ? styles.right : undefined}
          />
        ))}
        {rowActions && (
          <Column
            key="__actions"
            header={rowActionsHeader}
            headerClassName={styles.actionsHead}
            className={styles.actionsCell}
            body={(row) => <div className={styles.rowActions}>{rowActions(row)}</div>}
          />
        )}
      </DataTable>
    );
  }

  return (
    <section className={styles.card} aria-label={ariaLabel || title}>
      {(title || subtitle) && (
        <header className={styles.head}>
          {title && <h2 className={styles.title}>{title}</h2>}
          {subtitle && <div className={styles.subtitle}>{subtitle}</div>}
        </header>
      )}
      {(filters || actions) && (
        <div className={styles.toolbar}>
          {filters && <div className={styles.filters}>{filters}</div>}
          {actions && <div className={styles.actions}>{actions}</div>}
        </div>
      )}
      <div className={styles.body}>{body}</div>
      {footnote && <footer className={styles.foot}>{footnote}</footer>}
    </section>
  );
}

/** Ana sütun hücresi: kalın başlık (isteğe bağlı bağlantı) + altında ikincil satır. */
export function MainCell({ title, sub, href }) {
  return (
    <div className={styles.cellMain}>
      {href ? <Link href={href}>{title || "—"}</Link> : <b>{title || "—"}</b>}
      {sub ? <span>{sub}</span> : null}
    </div>
  );
}

/** Aktif / pasif rozeti (metin + ikon). */
export function ActiveBadge({ value }) {
  return <StatusBadge group="active" value={String(Boolean(value))} />;
}
