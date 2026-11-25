"use client";

import { useState } from "react";
import { Button } from "primereact/button";
import { Paginator } from "primereact/paginator";
import { SelectButton } from "primereact/selectbutton";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState from "@/components/feedback/ScreenState";
import ReasonDialog from "@/components/feedback/ReasonDialog";
import Card from "@/components/ui/Card";
import StatusBadge from "@/components/ui/StatusBadge";
import { formatRelativeDateTime } from "@/lib/formatting/date";
import { isConflict } from "@/lib/http/apiError";
import { pageMeta, toList } from "@/lib/http/page";
import RecordTable from "./RecordTable";
import RegistrationApproveDialog from "./RegistrationApproveDialog";
import { REGISTRATION_PAGE_SIZE, useRegistrationQueue } from "../hooks/useRegistrationQueue";
import { useRegistrationDecision } from "../action/useRegistrationDecision";
import styles from "../style/registration.module.scss";

const STATUS_FILTERS = [
  { value: "EMAIL_VERIFIED", label: "Onay bekleyen" },
  { value: "PENDING_EMAIL_VERIFICATION", label: "E-posta bekleniyor" },
  { value: "APPROVED", label: "Onaylanan" },
  { value: "REJECTED", label: "Reddedilen" },
  { value: "ALL", label: "Tümü" },
];

const canReject = (r) => r.status === "EMAIL_VERIFIED" || r.status === "PENDING_EMAIL_VERIFICATION";

/** ADMIN "Hesap Kayıt Talepleri" (/kayit-talepleri): durum filtresi, rol vererek onay, ret. */
export default function RegistrationQueueScreen() {
  const [status, setStatus] = useState("EMAIL_VERIFIED");
  const [page, setPage] = useState(0);
  const [approveTarget, setApproveTarget] = useState(null);
  const [rejectTarget, setRejectTarget] = useState(null);

  const query = useRegistrationQueue({ status, page });
  const { approve, reject } = useRegistrationDecision();
  const rows = toList(query.data);
  const meta = pageMeta(query.data);

  const columns = [
    {
      key: "name",
      header: "Ad soyad",
      main: true,
      render: (r) => (
        <>
          <span className={styles.title}>
            {r.firstName} {r.lastName}
          </span>
          <span className={styles.meta}>{r.email}</span>
        </>
      ),
    },
    { key: "created", header: "Talep", render: (r) => formatRelativeDateTime(r.createdAt) },
    { key: "verified", header: "E-posta doğrulama", render: (r) => (r.emailVerifiedAt ? formatRelativeDateTime(r.emailVerifiedAt) : "Doğrulanmadı") },
    {
      key: "status",
      header: "Durum",
      render: (r) => (
        <>
          <StatusBadge group="registrationStatus" value={r.status} />
          {r.rejectionReason && <span className={styles.meta}>{r.rejectionReason}</span>}
        </>
      ),
    },
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

  const emptyTitle = status === "EMAIL_VERIFIED" ? "Onay bekleyen kayıt talebi yok" : "Bu filtrede talep yok";

  return (
    <>
      <PageMeta title="Hesap Kayıt Talepleri" />
      <Card
        padded={false}
        footer={
          meta.totalPages > 1 ? (
            <div className={styles.pager}>
              <Paginator
                first={page * REGISTRATION_PAGE_SIZE}
                rows={REGISTRATION_PAGE_SIZE}
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
            onChange={(e) => {
              if (!e.value) return;
              setStatus(e.value);
              setPage(0);
            }}
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
          <ScreenState kind="empty" icon="pi-envelope" title={emptyTitle} message="Yeni kayıt talepleri e-posta doğrulandıktan sonra burada listelenir." bare />
        ) : (
          <RecordTable
            caption="Hesap kayıt talepleri"
            rows={rows}
            columns={columns}
            actions={(r) =>
              canReject(r) ? (
                <span className={styles.actionRow}>
                  <Button type="button" label="Reddet" size="small" className="p-button-secondary" onClick={() => setRejectTarget(r)} />
                  {r.status === "EMAIL_VERIFIED" && (
                    <Button type="button" label="Rol ver, onayla" icon="pi pi-check" size="small" onClick={() => setApproveTarget(r)} />
                  )}
                </span>
              ) : null
            }
          />
        )}
      </Card>

      {approveTarget && (
        <RegistrationApproveDialog
          key={approveTarget.id}
          request={approveTarget}
          submitting={approve.isPending}
          onHide={() => setApproveTarget(null)}
          onApprove={handleApprove}
        />
      )}

      <ReasonDialog
        visible={Boolean(rejectTarget)}
        onHide={() => setRejectTarget(null)}
        title="Kayıt talebini reddet"
        subtitle={rejectTarget?.email}
        label="Gerekçe"
        required={false}
        placeholder="İsteğe bağlı açıklama"
        confirmLabel="Reddet"
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
