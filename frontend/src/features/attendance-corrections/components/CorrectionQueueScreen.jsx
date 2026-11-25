"use client";

import { useState } from "react";
import { Button } from "primereact/button";
import { SelectButton } from "primereact/selectbutton";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import ReasonDialog from "@/components/feedback/ReasonDialog";
import Card from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import { errorMessage } from "@/lib/http/apiError";
import { pageMeta, toList } from "@/lib/http/page";
import { useCorrectionList } from "@/features/attendance-corrections/hooks/useCorrections";
import { useRejectCorrection } from "@/features/attendance-corrections/action/useCorrectionActions";
import CorrectionRow from "./CorrectionRow";
import ApproveCorrectionDialog from "./ApproveCorrectionDialog";
import styles from "@/features/attendance-corrections/style/corrections.module.scss";

const PAGE_SIZE = 20;
const SEGMENTS = [
  { value: "PENDING", label: "Bekleyen" },
  { value: "APPROVED", label: "Onaylanan" },
  { value: "REJECTED", label: "Reddedilen" },
];
const EMPTY = {
  PENDING: ["Bekleyen düzeltme talebi yok", "Personel ve velilerin talepleri burada sıralanır."],
  APPROVED: ["Onaylanan talep yok", "Onayladığınız talepler burada listelenir."],
  REJECTED: ["Reddedilen talep yok", "Reddettiğiniz talepler burada listelenir."],
};

export function Pager({ meta, page, setPage, busy }) {
  if (!meta || meta.totalPages <= 1) return null;
  return (
    <div className={styles.pager}>
      <span className={styles.meta}>
        Sayfa {meta.page + 1} / {meta.totalPages} · {meta.totalElements} talep
      </span>
      <div className={styles.line}>
        <Button type="button" icon="pi pi-chevron-left" label="Önceki" className="p-button-secondary" disabled={page === 0 || busy} onClick={() => setPage(page - 1)} />
        <Button
          type="button"
          icon="pi pi-chevron-right"
          iconPos="right"
          label="Sonraki"
          className="p-button-secondary"
          disabled={page + 1 >= meta.totalPages || busy}
          onClick={() => setPage(page + 1)}
        />
      </div>
    </div>
  );
}

/** ADMIN /yoklama-duzeltmeleri: Bekleyen/Onaylanan/Reddedilen; onay (durum seçilebilir) ve gerekçeli ret. */
export default function CorrectionQueueScreen() {
  const [status, setStatus] = useState("PENDING");
  const [page, setPage] = useState(0);
  const [approving, setApproving] = useState(null);
  const [rejecting, setRejecting] = useState(null);
  const [rejectError, setRejectError] = useState(null);
  const query = useCorrectionList({ status, page, size: PAGE_SIZE }, { live: status === "PENDING" });
  const reject = useRejectCorrection();
  const [emptyTitle, emptyText] = EMPTY[status];

  return (
    <>
      <PageMeta title="Yoklama Düzeltmeleri" crumb="Talepler ve onaylar" />
      <Card padded={false}>
        <div className={styles.head}>
          <SelectButton
            value={status}
            options={SEGMENTS}
            allowEmpty={false}
            aria-label="Talep durumu"
            onChange={(e) => {
              if (!e.value) return;
              setStatus(e.value);
              setPage(0);
            }}
          />
        </div>
        <QueryState
          query={query}
          isEmpty={(d) => toList(d).length === 0}
          empty={<ScreenState kind="empty" icon="pi-history" title={emptyTitle} message={emptyText} bare />}
        >
          {(data) => (
            <div className={styles.list}>
              {toList(data).map((c) => (
                <CorrectionRow
                  key={c.id}
                  correction={c}
                  admin
                  onApprove={setApproving}
                  onReject={(x) => {
                    setRejectError(null);
                    setRejecting(x);
                  }}
                />
              ))}
            </div>
          )}
        </QueryState>
        <Pager meta={query.data ? pageMeta(query.data) : null} page={page} setPage={setPage} busy={query.isFetching} />
      </Card>

      {approving && <ApproveCorrectionDialog key={approving.id} correction={approving} onHide={() => setApproving(null)} />}

      <ReasonDialog
        visible={Boolean(rejecting)}
        onHide={() => setRejecting(null)}
        title="Talebi reddet"
        subtitle={rejecting ? `${rejecting.studentFullName} · talep #${rejecting.id}` : undefined}
        icon="pi-times"
        label="Ret gerekçesi"
        placeholder="Talep sahibine bildirimle gönderilir"
        confirmLabel="Reddet"
        submitting={reject.isPending}
        onConfirm={async (reason) => {
          setRejectError(null);
          try {
            await reject.mutateAsync({ correction: rejecting, reason });
            setRejecting(null);
          } catch (err) {
            setRejectError(errorMessage(err, "Talep reddedilemedi."));
          }
        }}
      >
        {rejecting && <Notice tone="muted">“{rejecting.reason}”</Notice>}
        {rejectError && (
          <Notice tone="danger" title="Reddedilemedi">
            {rejectError}
          </Notice>
        )}
      </ReasonDialog>
    </>
  );
}
