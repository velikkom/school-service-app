"use client";

import { Suspense, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import Card from "@/components/ui/Card";
import StatusBadge from "@/components/ui/StatusBadge";
import Notice from "@/components/ui/Notice";
import { toastError } from "@/components/feedback/feedback";
import { PAYMENT_METHOD_LABELS } from "@/lib/formatting/labels";
import { formatDate, formatPeriod } from "@/lib/formatting/date";
import { formatMoney } from "@/lib/formatting/money";
import { toList } from "@/lib/http/page";
import { errorMessage } from "@/lib/http/apiError";
import { openHtmlDocument } from "@/lib/http/document";
import { contractsService } from "@/features/contracts/service/contractsService";
import { billingService } from "@/features/billing/service/billingService";
import { paymentsService } from "@/features/payments/service/paymentsService";
import { studentsService } from "@/features/students/service/studentsService";
import ChildSwitcher from "@/components/ui/ChildSwitcher";
import { useSelectedChild } from "@/features/students/hooks/useSelectedChild";
import { familyKeys } from "@/features/family/hooks/keys";
import styles from "../style/family.module.scss";

const methodLabel = (m) => PAYMENT_METHOD_LABELS[m] ?? (m ? "Diğer" : "—");

function useDocOpener() {
  const [busy, setBusy] = useState(null);
  const open = async (key, path) => {
    setBusy(key);
    try {
      await openHtmlDocument(path);
    } catch (err) {
      toastError("Belge açılamadı", errorMessage(err));
    } finally {
      setBusy(null);
    }
  };
  return { busy, open };
}

function ContractSummary({ student, statement }) {
  const contracts = useQuery({
    queryKey: familyKeys.contracts(student.id),
    queryFn: async () => toList(await contractsService.list({ studentId: student.id, size: 20 })),
  });
  return (
    <QueryState query={contracts} loadingRows={2} forbiddenHome="/servisim">
      {(list) => {
        const c = list.find((x) => x.status === "ACTIVE") ?? list.find((x) => x.status === "PENDING_APPROVAL") ?? list[0];
        const balance = statement?.currentBalance;
        const due = balance != null && Number(balance) > 0;
        return (
          <Card title="Sözleşme özeti" actions={c ? <StatusBadge group="contractStatus" value={c.status} /> : null}>
            {c ? (
              <div className={styles.summaryGrid}>
                <div className={styles.summaryItem}>
                  <span>Aylık ücret</span>
                  <b>{c.status === "PENDING_APPROVAL" ? "Onayda belirlenir" : formatMoney(c.monthlyFee)}</b>
                </div>
                <div className={styles.summaryItem}>
                  <span>İndirim</span>
                  <b>{formatMoney(c.discountAmount ?? "0")}</b>
                </div>
                <div className={styles.summaryItem}>
                  <span>Dönem</span>
                  <b className={styles.small}>
                    {formatDate(c.startDate)} – {c.endDate ? formatDate(c.endDate) : "süresiz"}
                  </b>
                </div>
                <div className={`${styles.summaryItem} ${due ? styles.balanceDue : ""}`}>
                  <span>Güncel bakiye</span>
                  <b>{balance != null ? formatMoney(balance) : "—"}</b>
                </div>
              </div>
            ) : (
              <p className={styles.small}>Bu öğrenci için sözleşme kaydı yok.</p>
            )}
            {c?.status === "CANCELLED" && c.rejectionReason && (
              <Notice tone="danger" title="Ret gerekçesi">
                {c.rejectionReason}
              </Notice>
            )}
            {due && <p className={styles.meta}>Pozitif bakiye ödenmemiş tutarı gösterir. Ödemeler kurum tarafından kaydedilir.</p>}
          </Card>
        );
      }}
    </QueryState>
  );
}

function ChargesCard({ student }) {
  const q = useQuery({
    queryKey: familyKeys.charges(student.id),
    queryFn: async () => toList(await billingService.charges({ studentId: student.id, size: 24 })),
  });
  return (
    <Card title="Tahakkuklar" padded={false}>
      <QueryState query={q} loadingRows={2} isEmpty={(d) => !d?.length} empty={<ScreenState kind="empty" bare compact icon="pi-calendar" title="Tahakkuk yok" message="" />}>
        {(rows) => (
          <div className={styles.rowList}>
            {rows.map((c) => (
              <div key={c.id} className={styles.rowItem}>
                <div className={styles.rowLine}>
                  <span>{formatPeriod(c.period)}</span>
                  <span className={styles.money}>{formatMoney(c.amount)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </QueryState>
    </Card>
  );
}

function PaymentsCard({ student, docs }) {
  const q = useQuery({
    queryKey: familyKeys.payments(student.id),
    queryFn: async () => toList(await paymentsService.list({ studentId: student.id, size: 50 })),
  });
  return (
    <Card title="Ödemeler" padded={false}>
      <QueryState query={q} loadingRows={2} isEmpty={(d) => !d?.length} empty={<ScreenState kind="empty" bare compact icon="pi-wallet" title="Ödeme kaydı yok" message="" />}>
        {(rows) => (
          <div className={styles.rowList}>
            {rows.map((p) => (
              <div key={p.id} className={styles.rowItem}>
                <div className={styles.rowLine}>
                  <span>
                    {formatDate(p.createdAt)} · {methodLabel(p.method)}
                  </span>
                  <span className={`${styles.money} ${p.status === "CANCELLED" ? styles.cancelled : ""}`}>{formatMoney(p.amount)}</span>
                </div>
                <div className={styles.rowLine}>
                  <StatusBadge group="paymentStatus" value={p.status} />
                  <Button
                    type="button"
                    icon="pi pi-file"
                    label="Makbuz"
                    className={`p-button-text ${styles.tap}`}
                    loading={docs.busy === `r${p.id}`}
                    disabled={Boolean(docs.busy)}
                    aria-label={`${formatDate(p.createdAt)} tarihli ödemenin makbuzunu aç`}
                    onClick={() => docs.open(`r${p.id}`, paymentsService.receiptPath(p.id))}
                  />
                </div>
                {p.note && <div className={styles.meta}>{p.note}</div>}
              </div>
            ))}
          </div>
        )}
      </QueryState>
    </Card>
  );
}

function entryDetail(e) {
  if (e.type === "CHARGE") return `Tahakkuk · ${formatPeriod(e.period)}`;
  return `Ödeme · ${methodLabel(e.method)}${e.paymentStatus === "CANCELLED" ? " (iptal)" : ""}`;
}

function StatementCard({ statementQuery, student, docs }) {
  return (
    <Card
      title="Ekstre"
      padded={false}
      actions={
        <Button
          type="button"
          icon="pi pi-print"
          label="Ekstreyi aç / yazdır"
          className={`p-button-secondary ${styles.tap}`}
          loading={docs.busy === "statement"}
          disabled={Boolean(docs.busy)}
          onClick={() => docs.open("statement", studentsService.statementDocumentPath(student.id))}
        />
      }
    >
      <QueryState
        query={statementQuery}
        loadingRows={3}
        isEmpty={(d) => !d?.entries?.length}
        empty={<ScreenState kind="empty" bare compact icon="pi-list" title="Ekstre hareketi yok" message="" />}
      >
        {(st) => {
          const entries = [...st.entries].reverse();
          return (
            <>
              <div className="hide-mobile">
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th scope="col">Tarih</th>
                      <th scope="col">Hareket</th>
                      <th scope="col" className={styles.num}>
                        Tutar
                      </th>
                      <th scope="col" className={styles.num}>
                        Bakiye
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((e, i) => (
                      <tr key={`${e.type}-${e.occurredAt}-${i}`}>
                        <td>{formatDate(e.occurredAt)}</td>
                        <td>{entryDetail(e)}</td>
                        <td className={`${styles.num} ${e.paymentStatus === "CANCELLED" ? styles.cancelled : ""}`}>
                          {e.type === "PAYMENT" ? "−" : ""}
                          {formatMoney(e.amount)}
                        </td>
                        <td className={styles.num}>{formatMoney(e.runningBalance)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className={`show-mobile ${styles.rowList}`}>
                {entries.map((e, i) => (
                  <div key={`${e.type}-${e.occurredAt}-${i}`} className={styles.rowItem}>
                    <div className={styles.rowLine}>
                      <span>{entryDetail(e)}</span>
                      <span className={`${styles.money} ${e.paymentStatus === "CANCELLED" ? styles.cancelled : ""}`}>
                        {e.type === "PAYMENT" ? "−" : ""}
                        {formatMoney(e.amount)}
                      </span>
                    </div>
                    <div className={styles.rowLine}>
                      <span className={styles.meta}>{formatDate(e.occurredAt)}</span>
                      <span className={styles.meta}>Bakiye {formatMoney(e.runningBalance)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </>
          );
        }}
      </QueryState>
    </Card>
  );
}

function ChildFinance({ student }) {
  const docs = useDocOpener();
  const statement = useQuery({
    queryKey: familyKeys.statement(student.id),
    queryFn: () => studentsService.statement(student.id),
  });
  return (
    <>
      <ContractSummary student={student} statement={statement.data} />
      <div className="grid-2">
        <ChargesCard student={student} />
        <PaymentsCard student={student} docs={docs} />
      </div>
      <StatementCard statementQuery={statement} student={student} docs={docs} />
      <p className={styles.meta}>Bilgiler salt okunurdur. Ödeme ve sözleşme değişiklikleri için kurumla iletişime geçin.</p>
    </>
  );
}

function FinanceInner() {
  const { query, children, selected, select } = useSelectedChild();
  return (
    <>
      <PageMeta title="Ödemeler ve Ekstre" />
      <div className={`${styles.page} ${styles.pageWide}`}>
        <QueryState
          query={query}
          isEmpty={() => children.length === 0}
          forbiddenHome="/servisim"
          empty={<ScreenState kind="empty" icon="pi-wallet" title="Finans kaydı yok" message="Aktif öğrenciniz bulunmuyor." />}
        >
          {() => (
            <>
              <ChildSwitcher items={children} selectedId={selected?.id} onSelect={select} />
              {selected && <ChildFinance key={selected.id} student={selected} />}
            </>
          )}
        </QueryState>
      </div>
    </>
  );
}

/** /odemeler (PARENT) — sözleşme özeti, tahakkuklar, ödemeler, ekstre; makbuz/ekstre HTML belge olarak açılır. */
export default function FamilyFinanceScreen() {
  return (
    <Suspense fallback={<ScreenState kind="loading" rows={3} />}>
      <FinanceInner />
    </Suspense>
  );
}
