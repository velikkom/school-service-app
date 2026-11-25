"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import ManagementList, { MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import { SelectFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { formatDate, todayISO } from "@/lib/formatting/date";
import { formatMoney } from "@/lib/formatting/money";
import { optionsOf } from "@/lib/formatting/labels";
import { toList } from "@/lib/http/page";
import { contractsService } from "@/features/contracts/service/contractsService";
import { studentsService } from "@/features/students/service/studentsService";
import { CONTRACT_INVALIDATE, contractKeys } from "@/features/contracts/hooks/keys";
import { contractCreateSchema, contractUpdateSchema } from "@/features/contracts/schemas/contractSchema";

const STATUS_OPTIONS = optionsOf("contractStatus");

/** /sozlesmeler (ADMIN) — sözleşme oluştur/düzenle/iptal. Aynı öğrencide çakışan aktif sözleşme 409. */
export default function ContractManagementScreen() {
  return (
    <>
      <PageMeta title="Sözleşmeler" crumb="Finans" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <ContractList />
      </Suspense>
    </>
  );
}

function ContractList() {
  const list = useListState({ studentId: "", status: "" });
  const query = useListQuery(contractKeys.list(), contractsService.list, list.params);
  const students = useLookup(["students"], () => studentsService.list({ size: 100, sort: "fullName,asc" }), { label: (s) => s.fullName });
  const dlg = useDialogParams();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;
  const cancel = useEntityMutation((id) => contractsService.cancel(id), CONTRACT_INVALIDATE);

  const doCancel = (c) =>
    runConfirmed(
      {
        title: "Sözleşmeyi iptal et",
        message: `${c.studentFullName ?? "Öğrenci"} için ${formatMoney(c.monthlyFee)} / ay sözleşme iptal edilecek. Sonraki dönemler için tahakkuk oluşmaz; mevcut tahakkuklar değişmez. Geri alınamaz.`,
        acceptLabel: "İptal et",
        success: "Sözleşme iptal edildi",
        failure: "Sözleşme iptal edilemedi",
      },
      () => cancel.mutateAsync(c.id)
    );

  return (
    <>
      <ManagementList
        ariaLabel="Sözleşmeler"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={
          <>
            <SelectFilter label="Öğrenci" value={list.filters.studentId} onChange={(v) => list.setFilter("studentId", v)} options={students.options} filter loading={students.query.isPending} />
            <SelectFilter label="Durum" value={list.filters.status} onChange={(v) => list.setFilter("status", v)} options={STATUS_OPTIONS} />
          </>
        }
        actions={<Button label="Sözleşme oluştur" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-file"
        emptyMessage="Öğrenci için aylık ücret sözleşmesi oluşturun."
        emptyAction={<Button label="Sözleşme oluştur" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          { key: "student", header: "Öğrenci", main: true, body: (c) => <MainCell title={c.studentFullName ?? `Öğrenci #${c.studentId}`} sub={`Sözleşme #${c.id}`} href={`/ogrenciler/${c.studentId}?tab=finans`} /> },
          {
            key: "fee",
            header: "Aylık ücret",
            body: (c) => <MainCell title={formatMoney(c.monthlyFee)} sub={Number(c.discountAmount) > 0 ? `İndirim ${formatMoney(c.discountAmount)}` : undefined} />,
          },
          { key: "period", header: "Dönem", body: (c) => `${formatDate(c.startDate)} – ${c.endDate ? formatDate(c.endDate) : "süresiz"}` },
          {
            key: "status",
            header: "Durum",
            body: (c) => (
              <div className="stack" style={{ gap: 2 }}>
                <StatusBadge group="contractStatus" value={c.status} />
                {c.rejectionReason && <span className="text-xs muted">{c.rejectionReason}</span>}
              </div>
            ),
          },
        ]}
        rowActions={(c) =>
          c.status === "PENDING_APPROVAL" ? (
            <Link href="/ogrenci-kayit-talepleri" className="p-button p-button-text p-button-sm">
              Başvuruya git
            </Link>
          ) : c.status === "ACTIVE" ? (
            <>
              <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(c.id)} />
              <Button label="İptal et" size="small" text severity="danger" onClick={() => doCancel(c)} />
            </>
          ) : null
        }
        footnote="Aynı öğrencide tarihleri çakışan iki aktif sözleşme olamaz. Yalnız aktif sözleşmeler düzenlenebilir."
      />
      <ContractFormDialog visible={dlg.isNew || Boolean(dlg.editId)} editId={dlg.editId} fromList={fromList} onHide={dlg.close} students={students} />
    </>
  );
}

function ContractFormDialog({ visible, editId, fromList, onHide, students }) {
  const { record, isLoading, error } = useRecord(contractKeys.detail(), contractsService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Sözleşmeyi düzenle" isLoading={isLoading} error={error}>
      {editId ? <ContractEditForm key={editId} record={record} onHide={onHide} /> : <ContractCreateForm onHide={onHide} students={students} />}
    </RecordGate>
  );
}

function ContractCreateForm({ onHide, students }) {
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(contractCreateSchema),
    defaultValues: { studentId: "", monthlyFee: "", discountAmount: "", startDate: todayISO(), endDate: "" },
  });
  const save = useEntityMutation((body) => contractsService.create(body), CONTRACT_INVALIDATE);
  const options = students.items.filter((s) => s.isActive).map((s) => ({ value: String(s.id), label: s.fullName }));
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await save.mutateAsync({
        studentId: Number(v.studentId),
        monthlyFee: v.monthlyFee,
        discountAmount: v.discountAmount || undefined,
        startDate: v.startDate,
        endDate: v.endDate || undefined,
      });
      toastSuccess("Sözleşme oluşturuldu");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError, "Sözleşme oluşturulamadı."));
    }
  });
  return (
    <AppDialog visible onHide={onHide} title="Sözleşme oluştur" icon="pi-file" tone="ok" onSubmit={onSubmit} submitting={save.isPending} formId="contract-form" wide>
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Öğrenci" error={errors.studentId?.message} required full htmlFor="ct-student">
          <Controller
            name="studentId"
            control={control}
            render={({ field }) => (
              <Dropdown inputId="ct-student" value={field.value} options={options} filter placeholder={students.query.isPending ? "Yükleniyor…" : "Öğrenci seçin"} emptyMessage="Aktif öğrenci yok" onChange={(e) => field.onChange(e.value ?? "")} />
            )}
          />
        </Field>
        <MoneyFields register={register} errors={errors} />
        <Field label="Başlangıç" error={errors.startDate?.message} required>
          <InputText type="date" {...register("startDate")} />
        </Field>
        <Field label="Bitiş" error={errors.endDate?.message} help="Boş = süresiz.">
          <InputText type="date" {...register("endDate")} />
        </Field>
      </FormGrid>
    </AppDialog>
  );
}

function MoneyFields({ register, errors }) {
  return (
    <>
      <Field label="Aylık ücret (TL)" error={errors.monthlyFee?.message} required>
        <InputText inputMode="decimal" placeholder="0,00" {...register("monthlyFee")} />
      </Field>
      <Field label="İndirim (TL)" error={errors.discountAmount?.message}>
        <InputText inputMode="decimal" placeholder="0,00" {...register("discountAmount")} />
      </Field>
    </>
  );
}

function ContractEditForm({ record, onHide }) {
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(contractUpdateSchema),
    defaultValues: { monthlyFee: record?.monthlyFee ?? "", discountAmount: record?.discountAmount ?? "", endDate: record?.endDate ?? "" },
  });
  const save = useEntityMutation((body) => contractsService.update(record.id, body), CONTRACT_INVALIDATE);
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await save.mutateAsync({ monthlyFee: v.monthlyFee, discountAmount: v.discountAmount || undefined, endDate: v.endDate || undefined });
      toastSuccess("Sözleşme güncellendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError));
    }
  });
  return (
    <AppDialog
      visible
      onHide={onHide}
      title="Sözleşmeyi düzenle"
      subtitle={`${record?.studentFullName ?? ""} · başlangıç ${formatDate(record?.startDate)}`}
      icon="pi-pencil"
      tone="info"
      onSubmit={onSubmit}
      submitting={save.isPending}
      formId="contract-edit-form"
    >
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <MoneyFields register={register} errors={errors} />
        <Field label="Bitiş" error={errors.endDate?.message} help="Boş bırakılırsa değişmez.">
          <InputText type="date" min={record?.startDate} {...register("endDate")} />
        </Field>
      </FormGrid>
      <p className="text-xs muted">Ücret değişikliği yalnız sonraki tahakkukları etkiler.</p>
    </AppDialog>
  );
}
