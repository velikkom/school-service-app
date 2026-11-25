"use client";

import { Suspense, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastError, toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import ManagementList, { MainCell } from "@/components/data/ManagementList";
import { SelectFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { formatDateTime } from "@/lib/formatting/date";
import { formatMoney } from "@/lib/formatting/money";
import { PAYMENT_METHOD_LABELS, mapOptions } from "@/lib/formatting/labels";
import { errorMessage, isConflict } from "@/lib/http/apiError";
import { openHtmlDocument } from "@/lib/http/document";
import { paymentsService } from "@/features/payments/service/paymentsService";
import { studentsService } from "@/features/students/service/studentsService";
import { PAYMENT_INVALIDATE, paymentKeys } from "@/features/payments/hooks/keys";
import { paymentSchema } from "@/features/payments/schemas/paymentSchema";

const METHOD_OPTIONS = mapOptions(PAYMENT_METHOD_LABELS);

/** Idempotency-Key: dialog her açıldığında bir kez üretilir; tekrar gönderimde aynı kalır. */
export function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return `pay-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** Makbuz/ekstre belgesini yeni sekmede açar ("Aç / yazdır"; PDF değil). */
export async function openDocument(path) {
  try {
    await openHtmlDocument(path);
  } catch (err) {
    toastError("Belge açılamadı", errorMessage(err, "Belge açılamadı."));
  }
}

/** /odemeler (ADMIN) — ödeme listesi, "Ödeme kaydet", iptal (onaylı), makbuz. */
export default function PaymentsScreen() {
  return (
    <>
      <PageMeta title="Ödemeler" crumb="Finans" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <PaymentList />
      </Suspense>
    </>
  );
}

function useStudents() {
  return useLookup(["students"], () => studentsService.list({ size: 100, sort: "fullName,asc" }), { label: (s) => s.fullName });
}

function PaymentList() {
  const list = useListState({ studentId: "" });
  const query = useListQuery(paymentKeys.list(), paymentsService.list, list.params);
  const students = useStudents();
  const dlg = useDialogParams();
  const cancel = useEntityMutation((id) => paymentsService.cancel(id), PAYMENT_INVALIDATE);
  const nameOf = (sid) => students.byId.get(String(sid))?.fullName ?? `Öğrenci #${sid}`;

  const doCancel = (p) =>
    runConfirmed(
      {
        title: "Ödemeyi iptal et",
        message: `${nameOf(p.studentId)} · ${formatMoney(p.amount)} ödemesi silinmez, “İptal” olarak işaretlenir ve bakiye yeniden hesaplanır. Geri alınamaz.`,
        acceptLabel: "İptal et",
        success: "Ödeme iptal edildi",
        failure: "Ödeme iptal edilemedi",
      },
      () => cancel.mutateAsync(p.id)
    );

  return (
    <>
      <ManagementList
        ariaLabel="Ödemeler"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={<SelectFilter label="Öğrenci" value={list.filters.studentId} onChange={(v) => list.setFilter("studentId", v)} options={students.options} filter loading={students.query.isPending} />}
        actions={<Button label="Ödeme kaydet" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-wallet"
        emptyTitle="Henüz ödeme yok"
        emptyMessage="Tahsilatları “Ödeme kaydet” ile girin."
        emptyAction={<Button label="Ödeme kaydet" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          { key: "date", header: "Tarih", body: (p) => formatDateTime(p.createdAt) },
          { key: "student", header: "Öğrenci", main: true, body: (p) => <MainCell title={nameOf(p.studentId)} sub={p.note} href={`/ogrenciler/${p.studentId}?tab=finans`} /> },
          { key: "amount", header: "Tutar", align: "right", body: (p) => <b className="mono">{formatMoney(p.amount)}</b> },
          { key: "method", header: "Yöntem", body: (p) => PAYMENT_METHOD_LABELS[p.method] ?? "—" },
          { key: "status", header: "Durum", body: (p) => <StatusBadge group="paymentStatus" value={p.status} /> },
        ]}
        rowActions={(p) =>
          p.status === "COMPLETED" ? (
            <>
              <Button label="Makbuz" icon="pi pi-receipt" size="small" text onClick={() => openDocument(paymentsService.receiptPath(p.id))} />
              <Button label="İptal" size="small" text severity="danger" onClick={() => doCancel(p)} />
            </>
          ) : null
        }
        footnote="Öğrenci bazlı ekstre: Öğrenci detayı → Sözleşme ve ekstre. Makbuz yazdırılabilir HTML belge olarak açılır."
      />
      {dlg.isNew && <PaymentFormDialog presetStudentId={dlg.get("ogrenci")} studentOptions={students.items} onHide={dlg.close} />}
    </>
  );
}

/**
 * Ödeme kaydet dialogu. Idempotency-Key açılışta üretilir ve aynı dialogda yapılan her gönderimde
 * (ör. ağ hatası sonrası tekrar) aynı kalır → sunucu çift kayıt oluşturmaz. Borçtan fazla tutar 409.
 */
export function PaymentFormDialog({ presetStudentId, studentOptions = [], onHide }) {
  const [idempotencyKey] = useState(newIdempotencyKey);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(paymentSchema),
    defaultValues: { studentId: presetStudentId ? String(presetStudentId) : "", amount: "", method: "CASH", note: "" },
  });
  const studentId = useWatch({ control, name: "studentId" });
  const statement = useQuery({
    queryKey: ["students", "statement", String(studentId)],
    queryFn: () => studentsService.statement(studentId),
    enabled: Boolean(studentId),
  });
  const balance = statement.data?.currentBalance;
  const options = studentOptions.filter((s) => s.isActive || String(s.id) === String(presetStudentId)).map((s) => ({ value: String(s.id), label: s.fullName }));
  const save = useEntityMutation((body) => paymentsService.create(body, idempotencyKey), PAYMENT_INVALIDATE);

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await save.mutateAsync({ studentId: Number(v.studentId), amount: v.amount, method: v.method, note: v.note || undefined });
      toastSuccess("Ödeme kaydedildi");
      onHide();
    } catch (err) {
      const text = formErrorText(err, setError, "Ödeme kaydedilemedi.");
      setServerError(isConflict(err) && err.error === "CONFLICT" ? { title: "Tutar kalan borçtan fazla olabilir", text } : { title: "Kaydedilemedi", text });
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title="Ödeme kaydet"
      subtitle={studentId && balance != null ? `Kalan borç ${formatMoney(balance)}` : undefined}
      icon="pi-wallet"
      tone="ok"
      submitLabel="Ödemeyi kaydet"
      onSubmit={onSubmit}
      submitting={save.isPending}
      formId="payment-form"
    >
      {serverError && (
        <Notice tone="danger" title={serverError.title}>
          {serverError.text}
        </Notice>
      )}
      <FormGrid>
        <Field label="Öğrenci" error={errors.studentId?.message} required full htmlFor="pay-student">
          <Controller
            name="studentId"
            control={control}
            render={({ field }) => (
              <Dropdown inputId="pay-student" value={field.value} options={options} filter placeholder="Öğrenci seçin" emptyMessage="Öğrenci yok" onChange={(e) => field.onChange(e.value ?? "")} />
            )}
          />
        </Field>
        <Field label="Tutar (TL)" error={errors.amount?.message} required>
          <InputText inputMode="decimal" placeholder="0,00" {...register("amount")} />
        </Field>
        <Field label="Yöntem" error={errors.method?.message} required htmlFor="pay-method">
          <Controller name="method" control={control} render={({ field }) => <Dropdown inputId="pay-method" value={field.value} options={METHOD_OPTIONS} onChange={(e) => field.onChange(e.value)} />} />
        </Field>
        <Field label="Not" error={errors.note?.message} full>
          <InputTextarea rows={2} autoResize={false} maxLength={500} {...register("note")} />
        </Field>
      </FormGrid>
      <p className="text-xs muted">Kalan borçtan fazla tutar kaydedilmez. Çift gönderime karşı istek tek kullanımlık anahtarla gönderilir.</p>
    </AppDialog>
  );
}
