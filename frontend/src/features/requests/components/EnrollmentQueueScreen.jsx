"use client";

import { useState } from "react";
import { Button } from "primereact/button";
import { Paginator } from "primereact/paginator";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import ReasonDialog from "@/components/feedback/ReasonDialog";
import Card from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import { formatDate } from "@/lib/formatting/date";
import { formatMoney } from "@/lib/formatting/money";
import { isConflict } from "@/lib/http/apiError";
import { pageMeta, toList } from "@/lib/http/page";
import RecordTable from "./RecordTable";
import EnrollmentApproveDialog from "./EnrollmentApproveDialog";
import { QUEUE_PAGE_SIZE, useEnrollmentQueue } from "../hooks/useQueues";
import { useEnrollmentDecision } from "../action/useDecisions";
import styles from "../style/requests.module.scss";

/** ADMIN "Öğrenci Başvuruları" (/ogrenci-kayit-talepleri): PENDING_APPROVAL sözleşmeler, onay/ret. */
export default function EnrollmentQueueScreen() {
  const [page, setPage] = useState(0);
  const [approveTarget, setApproveTarget] = useState(null);
  const [rejectTarget, setRejectTarget] = useState(null);

  const query = useEnrollmentQueue({ page });
  const { approve, reject } = useEnrollmentDecision();
  const rows = toList(query.data);
  const meta = pageMeta(query.data);

  const columns = [
    {
      key: "student",
      header: "Öğrenci",
      main: true,
      render: (c) => (
        <>
          <span className={styles.title}>{c.studentFullName}</span>
          <span className={styles.meta}>Başvuru #{c.id}</span>
        </>
      ),
    },
    { key: "start", header: "Başlangıç", render: (c) => formatDate(c.startDate) },
    { key: "fee", header: "Aylık ücret", render: (c) => (c.monthlyFee != null ? <span className="mono">{formatMoney(c.monthlyFee)}</span> : "Onayda belirlenir") },
    { key: "status", header: "Durum", render: (c) => <StatusBadge group="contractStatus" value={c.status} /> },
  ];

  const handleApprove = async (body) => {
    try {
      await approve.mutateAsync({ id: approveTarget.id, body });
      setApproveTarget(null);
    } catch (err) {
      if (isConflict(err)) setApproveTarget(null);
      throw err;
    }
  };

  return (
    <>
      <PageMeta title="Öğrenci Başvuruları" subtitle="İlk öz-kayıt ve ek çocuk başvuruları" />
      <div className="stack-lg">
        <Card
          title="Onay bekleyen başvurular"
          subtitle={query.data ? `${meta.totalElements} başvuru` : undefined}
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
          {query.isPending && !query.data ? (
            <ScreenState kind="loading" rows={4} bare />
          ) : query.isError && !query.data ? (
            query.error?.status === 403 ? (
              <ScreenState kind="forbidden" homeHref="/dashboard" bare />
            ) : (
              <ScreenState kind="error" error={query.error} onRetry={query.refetch} bare />
            )
          ) : rows.length === 0 ? (
            <ScreenState kind="empty" icon="pi-id-card" title="Bekleyen başvuru yok" message="Velilerin yeni öğrenci başvuruları burada listelenir." bare />
          ) : (
            <RecordTable
              caption="Onay bekleyen öğrenci başvuruları"
              rows={rows}
              columns={columns}
              actions={(c) => (
                <span className={styles.actionRow}>
                  <Button type="button" label="Reddet" size="small" className="p-button-secondary" onClick={() => setRejectTarget(c)} />
                  <Button type="button" label="İncele ve onayla" icon="pi pi-search" size="small" onClick={() => setApproveTarget(c)} />
                </span>
              )}
            />
          )}
        </Card>
        <Notice tone="info">
          İlk öz-kayıtta veli, başvurusu onaylanana kadar uygulamanın geri kalanını kullanamaz. Reddedilen başvuruda öğrenci pasife alınır ve
          veli bağı kaldırılır; veli sonucu bildirimden takip eder.
        </Notice>
      </div>

      {approveTarget && (
        <EnrollmentApproveDialog
          key={approveTarget.id}
          contract={approveTarget}
          submitting={approve.isPending}
          onHide={() => setApproveTarget(null)}
          onApprove={handleApprove}
        />
      )}

      <ReasonDialog
        visible={Boolean(rejectTarget)}
        onHide={() => setRejectTarget(null)}
        title="Başvuruyu reddet"
        subtitle={rejectTarget?.studentFullName}
        label="Ret gerekçesi"
        placeholder="Veliye iletilecek gerekçe"
        confirmLabel="Reddet"
        warning={{ tone: "warn", title: "Geri alınamaz", text: "Öğrenci pasife alınır ve veli bağı kaldırılır. Veli sonucu ve gerekçeyi bildirim olarak görür." }}
        submitting={reject.isPending}
        onConfirm={(reason) =>
          reject.mutate(
            { id: rejectTarget.id, reason },
            {
              onSuccess: () => setRejectTarget(null),
              onError: (err) => {
                if (isConflict(err)) setRejectTarget(null);
              },
            }
          )
        }
      />
    </>
  );
}
