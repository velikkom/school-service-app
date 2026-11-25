"use client";

import { useState } from "react";
import { Button } from "primereact/button";
import { SelectButton } from "primereact/selectbutton";
import { Paginator } from "primereact/paginator";
import { InputTextarea } from "primereact/inputtextarea";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import AppDialog from "@/components/feedback/AppDialog";
import ReasonDialog from "@/components/feedback/ReasonDialog";
import Card from "@/components/ui/Card";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import { formatDate, formatRelativeDateTime } from "@/lib/formatting/date";
import { labelOf } from "@/lib/formatting/labels";
import { isConflict } from "@/lib/http/apiError";
import { pageMeta, toList } from "@/lib/http/page";
import RecordTable from "./RecordTable";
import { QUEUE_PAGE_SIZE, useStopChangeQueue } from "../hooks/useQueues";
import { useStopChangeDecision } from "../action/useDecisions";
import styles from "../style/requests.module.scss";

const STATUS_FILTERS = [
  { value: "PENDING", label: "Bekleyen" },
  { value: "APPROVED", label: "Onaylanan" },
  { value: "REJECTED", label: "Reddedilen" },
];

const EMPTY_TEXT = {
  PENDING: ["Bekleyen durak talebi yok", "Velilerin yeni durak değişikliği talepleri burada listelenir."],
  APPROVED: ["Onaylanan talep yok", "Onayladığınız talepler burada görünür."],
  REJECTED: ["Reddedilen talep yok", "Reddettiğiniz talepler burada görünür."],
};

function ApproveDialog({ request, submitting, onHide, onConfirm }) {
  const [note, setNote] = useState("");
  return (
    <AppDialog
      visible
      onHide={onHide}
      title="Durak değişikliğini onayla"
      subtitle={`${request.studentFullName} · ${labelOf("direction", request.direction).short}`}
      icon="pi-check"
      tone="ok"
      submitLabel="Onayla"
      submitIcon="pi-check"
      submitting={submitting}
      onSubmit={() => onConfirm(note.trim())}
    >
      <Notice tone="info">
        {formatDate(request.effectiveDate)} itibarıyla <b>{request.requestedStopName}</b> durağıyla yeni atama açılır; mevcut atama bir
        önceki gün kapanır. Onay anında durak, yön ve tarih yeniden doğrulanır.
      </Notice>
      <Field label="Not (isteğe bağlı)" help={`${note.length}/500 · Veliye bildirimle iletilir.`}>
        <InputTextarea value={note} onChange={(e) => setNote(e.target.value)} rows={3} maxLength={500} autoResize={false} />
      </Field>
    </AppDialog>
  );
}

/** ADMIN "Durak Değişiklikleri" (/veli-talepleri): Bekleyen / Onaylanan / Reddedilen, onay ve ret. */
export default function StopChangeQueueScreen() {
  const [status, setStatus] = useState("PENDING");
  const [page, setPage] = useState(0);
  const [approveTarget, setApproveTarget] = useState(null);
  const [rejectTarget, setRejectTarget] = useState(null);

  const query = useStopChangeQueue({ status, page });
  const { approve, reject } = useStopChangeDecision();
  const rows = toList(query.data);
  const meta = pageMeta(query.data);

  const changeStatus = (value) => {
    if (!value) return;
    setStatus(value);
    setPage(0);
  };

  const closeOnConflict = (close) => (err) => {
    if (isConflict(err)) close();
  };

  const columns = [
    {
      key: "student",
      header: "Öğrenci",
      main: true,
      render: (r) => (
        <>
          <span className={styles.title}>{r.studentFullName}</span>
          <span className={styles.meta}>
            {r.requestedByFullName || "Veli"} · {formatRelativeDateTime(r.createdAt)}
          </span>
        </>
      ),
    },
    { key: "direction", header: "Yön", render: (r) => labelOf("direction", r.direction).short },
    {
      key: "stops",
      header: "Mevcut → istenen durak",
      render: (r) => (
        <>
          <span className={styles.stopChange}>
            <span>{r.currentStopName || "Atama yok"}</span>
            <i className="pi pi-arrow-right" aria-label="yerine" />
            <b>{r.requestedStopName}</b>
          </span>
          {r.requestedRouteName && <span className={styles.meta}>{r.requestedRouteName}</span>}
        </>
      ),
    },
    { key: "effective", header: "Yürürlük", render: (r) => formatDate(r.effectiveDate) },
    { key: "reason", header: "Gerekçe", render: (r) => r.reason || "—" },
    {
      key: "status",
      header: "Durum",
      render: (r) => (
        <>
          <StatusBadge group="requestStatus" value={r.status} />
          {r.decisionNote && <span className={styles.meta}>{r.decisionNote}</span>}
          {r.decidedAt && <span className={styles.meta}>{formatRelativeDateTime(r.decidedAt)}</span>}
        </>
      ),
    },
  ];

  const [emptyTitle, emptyText] = EMPTY_TEXT[status];

  return (
    <>
      <PageMeta title="Durak Değişiklikleri" subtitle="Veli talepleri" />
      <Card
        padded={false}
        footer={
          meta.totalPages > 1 ? (
            <div className={styles.pager}>
              <Paginator
                first={page * QUEUE_PAGE_SIZE}
                rows={QUEUE_PAGE_SIZE}
                totalRecords={meta.totalElements}
                onPageChange={(e) => setPage(e.page)}
                template="PrevPageLink CurrentPageReport NextPageLink"
                currentPageReportTemplate="{currentPage} / {totalPages}"
              />
            </div>
          ) : null
        }
      >
        <div className={styles.cardHead}>
          <SelectButton
            className={styles.segment}
            value={status}
            options={STATUS_FILTERS}
            onChange={(e) => changeStatus(e.value)}
            allowEmpty={false}
            aria-label="Durum filtresi"
          />
        </div>
        {query.isPending && !query.data ? (
          <ScreenState kind="loading" rows={4} bare />
        ) : query.isError && !query.data ? (
          query.error?.status === 403 ? (
            <ScreenState kind="forbidden" homeHref="/dashboard" bare />
          ) : (
            <ScreenState kind="error" error={query.error} onRetry={query.refetch} bare />
          )
        ) : rows.length === 0 ? (
          <ScreenState kind="empty" icon="pi-map-marker" title={emptyTitle} message={emptyText} bare />
        ) : (
          <RecordTable
            caption="Durak değişikliği talepleri"
            rows={rows}
            columns={columns}
            actions={(r) =>
              r.status === "PENDING" ? (
                <span className={styles.actionRow}>
                  <Button type="button" label="Reddet" size="small" className="p-button-secondary" onClick={() => setRejectTarget(r)} />
                  <Button type="button" label="Onayla" icon="pi pi-check" size="small" onClick={() => setApproveTarget(r)} />
                </span>
              ) : null
            }
          />
        )}
      </Card>

      {approveTarget && (
        <ApproveDialog
          key={approveTarget.id}
          request={approveTarget}
          submitting={approve.isPending}
          onHide={() => setApproveTarget(null)}
          onConfirm={(note) =>
            approve.mutate(
              { id: approveTarget.id, note },
              { onSuccess: () => setApproveTarget(null), onError: closeOnConflict(() => setApproveTarget(null)) }
            )
          }
        />
      )}

      <ReasonDialog
        visible={Boolean(rejectTarget)}
        onHide={() => setRejectTarget(null)}
        title="Durak değişikliğini reddet"
        subtitle={rejectTarget ? `${rejectTarget.studentFullName} · ${labelOf("direction", rejectTarget.direction).short}` : undefined}
        label="Ret gerekçesi"
        placeholder="Veliye iletilecek gerekçe"
        confirmLabel="Reddet"
        submitting={reject.isPending}
        onConfirm={(reason) =>
          reject.mutate(
            { id: rejectTarget.id, reason },
            { onSuccess: () => setRejectTarget(null), onError: closeOnConflict(() => setRejectTarget(null)) }
          )
        }
      />
    </>
  );
}
