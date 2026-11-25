"use client";

import { Suspense, useId, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ManagementList, { MainCell } from "@/components/data/ManagementList";
import { FilterField, SelectFilter } from "@/components/data/ListFilters";
import { useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { useEntityMutation } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { formatPeriod, todayISO } from "@/lib/formatting/date";
import { formatMoney } from "@/lib/formatting/money";
import { billingService } from "@/features/billing/service/billingService";
import { studentsService } from "@/features/students/service/studentsService";
import { BILLING_INVALIDATE, billingKeys } from "@/features/billing/hooks/keys";
import { billingRunSchema } from "@/features/billing/schemas/billingSchema";

/** /tahakkuklar (ADMIN) — aylık tahakkuk listesi + "Tahakkuk çalıştır" dialogu (sonuç: oluşan/atlanan). */
export default function BillingScreen() {
  return (
    <>
      <PageMeta title="Tahakkuklar" crumb="Finans" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <ChargeList />
      </Suspense>
    </>
  );
}

function ChargeList() {
  const list = useListState({ period: "", studentId: "" });
  const query = useListQuery(billingKeys.charges(), billingService.charges, list.params);
  const students = useLookup(["students"], () => studentsService.list({ size: 100, sort: "fullName,asc" }), { label: (s) => s.fullName });
  const [runOpen, setRunOpen] = useState(false);
  const periodId = useId();

  return (
    <div className="stack-lg">
      <ManagementList
        ariaLabel="Tahakkuklar"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={
          <>
            <FilterField label="Dönem" htmlFor={periodId}>
              <InputText id={periodId} type="month" value={list.filters.period} onChange={(e) => list.setFilter("period", e.target.value)} />
            </FilterField>
            <SelectFilter label="Öğrenci" value={list.filters.studentId} onChange={(v) => list.setFilter("studentId", v)} options={students.options} filter loading={students.query.isPending} />
          </>
        }
        actions={<Button label="Tahakkuk çalıştır" icon="pi pi-refresh" onClick={() => setRunOpen(true)} />}
        emptyIcon="pi-receipt"
        emptyTitle="Henüz tahakkuk yok"
        emptyMessage="Tahakkuklar her ay otomatik oluşur; gerekirse “Tahakkuk çalıştır” ile elle başlatın."
        columns={[
          { key: "period", header: "Dönem", body: (c) => formatPeriod(c.period) },
          {
            key: "student",
            header: "Öğrenci",
            main: true,
            body: (c) => (
              <MainCell title={students.byId.get(String(c.studentId))?.fullName ?? `Öğrenci #${c.studentId}`} sub={`Sözleşme #${c.contractId}`} href={`/ogrenciler/${c.studentId}?tab=finans`} />
            ),
          },
          { key: "amount", header: "Tutar", align: "right", body: (c) => <b className="mono">{formatMoney(c.amount)}</b> },
        ]}
      />
      <Notice tone="muted">Elle çalıştırma aynı dönem için ikinci kayıt oluşturmaz; mevcut tahakkuklar “atlanan” olarak sayılır.</Notice>
      {runOpen && <BillingRunDialog onHide={() => setRunOpen(false)} />}
    </div>
  );
}

export function BillingRunDialog({ onHide }) {
  const [serverError, setServerError] = useState(null);
  const [result, setResult] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(billingRunSchema), defaultValues: { period: todayISO().slice(0, 7) } });
  const run = useEntityMutation((period) => billingService.run(period), BILLING_INVALIDATE);

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      const res = await run.mutateAsync(v.period);
      setResult(res);
      toastSuccess(`${res.created} tahakkuk oluşturuldu, ${res.skipped} atlandı`);
    } catch (err) {
      setServerError(formErrorText(err, setError, "Tahakkuk çalıştırılamadı."));
    }
  });

  if (result) {
    return (
      <AppDialog visible onHide={onHide} title="Tahakkuk tamamlandı" subtitle={formatPeriod(result.period)} icon="pi-check" tone="ok" cancelLabel="Kapat">
        <div className="grid-2">
          <div>
            <div className="text-sm muted">Oluşan</div>
            <div className="mono" style={{ fontSize: 28, fontWeight: 800 }}>{result.created}</div>
          </div>
          <div>
            <div className="text-sm muted">Atlanan</div>
            <div className="mono" style={{ fontSize: 28, fontWeight: 800 }}>{result.skipped}</div>
          </div>
        </div>
        <p className="text-xs muted">Atlananlar: bu dönem için zaten tahakkuku olan aktif sözleşmeler.</p>
      </AppDialog>
    );
  }

  return (
    <AppDialog visible onHide={onHide} title="Aylık tahakkuk çalıştır" icon="pi-refresh" tone="warn" submitLabel="Çalıştır" onSubmit={onSubmit} submitting={run.isPending} formId="billing-run-form">
      {serverError && <Notice tone="danger" title="Çalıştırılamadı">{serverError}</Notice>}
      <Field label="Dönem" error={errors.period?.message} required>
        <InputText type="month" {...register("period")} />
      </Field>
      <Notice tone="warn" title="Onay">
        Seçilen dönem için tüm aktif sözleşmelere tahakkuk oluşturulur ve veli ekstrelerine yansır. Mevcut tahakkuklar değişmez.
      </Notice>
    </AppDialog>
  );
}
