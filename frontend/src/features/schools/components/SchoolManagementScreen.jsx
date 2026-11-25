"use client";

import { Suspense, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { InputSwitch } from "primereact/inputswitch";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import RecordGate from "@/components/data/RecordGate";
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import { ActiveFilter, SearchFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { toList } from "@/lib/http/page";
import { schoolsService } from "@/features/schools/service/schoolsService";
import { SCHOOL_INVALIDATE, schoolKeys } from "@/features/schools/hooks/keys";
import { SCHOOL_DEFAULTS, schoolSchema } from "@/features/schools/schemas/schoolSchema";

/** /okullar (ADMIN) — okul listesi + yeni/düzenle dialogu, pasife alma. */
export default function SchoolManagementScreen() {
  return (
    <>
      <PageMeta title="Okullar" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <SchoolList />
      </Suspense>
    </>
  );
}

function SchoolList() {
  const list = useListState({ q: "", isActive: "" });
  const query = useListQuery(schoolKeys.list(), schoolsService.list, list.params);
  const dlg = useDialogParams();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;
  const toggle = useEntityMutation(({ id, isActive }) => schoolsService.update(id, { isActive }), SCHOOL_INVALIDATE);

  const setActive = (row, isActive) =>
    runConfirmed(
      isActive
        ? { title: "Okulu aktifleştir", message: `${row.name} yeniden seçilebilir olacak.`, acceptLabel: "Aktifleştir", tone: "info", success: "Okul aktifleştirildi" }
        : {
            title: "Okulu pasife al",
            message: `${row.name} silinmez; pasif okullar veli başvuru sihirbazında ve yeni kayıtlarda seçilemez.`,
            acceptLabel: "Pasife al",
            success: "Okul pasife alındı",
          },
      () => toggle.mutateAsync({ id: row.id, isActive })
    );

  return (
    <>
      <ManagementList
        ariaLabel="Okullar"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={
          <>
            <SearchFilter label="Ara" placeholder="Okul adı" value={list.filters.q} onChange={(v) => list.setFilter("q", v)} />
            <ActiveFilter value={list.filters.isActive} onChange={(v) => list.setFilter("isActive", v)} />
          </>
        }
        actions={<Button label="Yeni okul" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-building"
        emptyMessage="İlk okulu “Yeni okul” ile ekleyin."
        emptyAction={<Button label="Yeni okul" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          { key: "name", header: "Okul", main: true, body: (s) => <MainCell title={s.name} sub={s.address} /> },
          { key: "phone", header: "Telefon", body: (s) => s.phone || "—" },
          {
            key: "status",
            header: "Durum",
            body: (s) => (
              <div className="stack" style={{ gap: 2 }}>
                <ActiveBadge value={s.isActive} />
                {!s.isActive && <span className="text-xs muted">Veli sihirbazında görünmez</span>}
              </div>
            ),
          },
        ]}
        rowActions={(s) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(s.id)} />
            {s.isActive ? (
              <Button label="Pasife al" size="small" text severity="danger" onClick={() => setActive(s, false)} />
            ) : (
              <Button label="Aktifleştir" size="small" text onClick={() => setActive(s, true)} />
            )}
          </>
        )}
        footnote="Okullar silinmez; kapatmak için pasife alın."
      />
      <SchoolFormDialog visible={dlg.isNew || Boolean(dlg.editId)} editId={dlg.editId} fromList={fromList} onHide={dlg.close} />
    </>
  );
}

function SchoolFormDialog({ visible, editId, fromList, onHide }) {
  const { record, isLoading, error } = useRecord(schoolKeys.detail(), schoolsService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Okulu düzenle" isLoading={isLoading} error={error}>
      <SchoolForm key={editId ?? "new"} record={editId ? record : null} editId={editId} onHide={onHide} />
    </RecordGate>
  );
}

function SchoolForm({ record, editId, onHide }) {
  const isEdit = Boolean(editId);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(schoolSchema),
    defaultValues: record
      ? { name: record.name ?? "", address: record.address ?? "", phone: record.phone ?? "", isActive: Boolean(record.isActive) }
      : SCHOOL_DEFAULTS,
  });
  const save = useEntityMutation((body) => (isEdit ? schoolsService.update(editId, body) : schoolsService.create(body)), SCHOOL_INVALIDATE);

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    const body = { name: v.name, address: v.address || undefined, phone: v.phone || undefined };
    if (isEdit) body.isActive = v.isActive;
    try {
      await save.mutateAsync(body);
      toastSuccess(isEdit ? "Okul güncellendi" : "Okul eklendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError));
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title={isEdit ? "Okulu düzenle" : "Yeni okul"}
      subtitle={isEdit ? record?.name : undefined}
      icon={isEdit ? "pi-pencil" : "pi-building"}
      tone={isEdit ? "info" : "ok"}
      onSubmit={onSubmit}
      submitting={save.isPending}
      formId="school-form"
    >
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Okul adı" error={errors.name?.message} required full>
          <InputText {...register("name")} />
        </Field>
        <Field label="Adres" error={errors.address?.message} full>
          <InputText {...register("address")} />
        </Field>
        <Field label="Telefon" error={errors.phone?.message}>
          <InputText type="tel" inputMode="tel" {...register("phone")} />
        </Field>
        {isEdit && (
          <Field label="Aktif" htmlFor="school-active" help="Pasif okul veli sihirbazında görünmez.">
            <Controller
              name="isActive"
              control={control}
              render={({ field }) => <InputSwitch inputId="school-active" checked={field.value} onChange={(e) => field.onChange(e.value)} />}
            />
          </Field>
        )}
      </FormGrid>
    </AppDialog>
  );
}
