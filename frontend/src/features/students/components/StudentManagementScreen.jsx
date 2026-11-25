"use client";

import { Suspense, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { Dropdown } from "primereact/dropdown";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import { ActiveFilter, SearchFilter, SelectFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { formatDate, todayISO } from "@/lib/formatting/date";
import { toList } from "@/lib/http/page";
import { studentsService } from "@/features/students/service/studentsService";
import { schoolsService } from "@/features/schools/service/schoolsService";
import { routesService } from "@/features/routes/service/routesService";
import { STUDENT_INVALIDATE, studentKeys } from "@/features/students/hooks/keys";
import { studentSchema, studentToBody, studentToForm } from "@/features/students/schemas/studentSchema";

/** /ogrenciler (ADMIN) — öğrenci listesi, yeni/düzenle dialogu, pasife alma. */
export default function StudentManagementScreen() {
  return (
    <>
      <PageMeta title="Öğrenciler" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <StudentList />
      </Suspense>
    </>
  );
}

/** Öğrenci pasife alma (onaylı) — liste ve detay ortak. */
export function useDeactivateStudent() {
  const m = useEntityMutation((id) => studentsService.deactivate(id), STUDENT_INVALIDATE);
  return (s) =>
    runConfirmed(
      {
        title: "Öğrenciyi pasife al",
        message: `${s.fullName} silinmez; listelerde pasif görünür, yeni atama ve sözleşmelerde seçilemez.`,
        acceptLabel: "Pasife al",
        success: "Öğrenci pasife alındı",
      },
      () => m.mutateAsync(s.id)
    );
}

function StudentList() {
  const list = useListState({ q: "", isActive: "", routeId: "" });
  const query = useListQuery(studentKeys.list(), studentsService.list, list.params);
  const routes = useLookup(["routes"], () => routesService.list({ size: 100, sort: "name,asc" }), { label: (r) => r.name });
  const dlg = useDialogParams();
  const deactivate = useDeactivateStudent();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;

  return (
    <>
      <ManagementList
        ariaLabel="Öğrenciler"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={
          <>
            <SearchFilter label="Ara" placeholder="Ad veya T.C." value={list.filters.q} onChange={(v) => list.setFilter("q", v)} />
            <SelectFilter label="Güzergâh" value={list.filters.routeId} onChange={(v) => list.setFilter("routeId", v)} options={routes.options} filter loading={routes.query.isPending} />
            <ActiveFilter value={list.filters.isActive} onChange={(v) => list.setFilter("isActive", v)} />
          </>
        }
        actions={<Button label="Yeni öğrenci" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-id-card"
        emptyMessage="İlk öğrenciyi “Yeni öğrenci” ile ekleyin."
        emptyAction={<Button label="Yeni öğrenci" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          {
            key: "name",
            header: "Öğrenci",
            main: true,
            body: (s) => <MainCell title={s.fullName} href={`/ogrenciler/${s.id}`} sub={[s.schoolName, s.grade].filter(Boolean).join(" · ") || "Okul belirtilmedi"} />,
          },
          { key: "birth", header: "Doğum", body: (s) => formatDate(s.birthDate) },
          { key: "tc", header: "T.C. kimlik", body: (s) => <span className="mono">{s.tcNo || "—"}</span> },
          { key: "status", header: "Durum", body: (s) => <ActiveBadge value={s.isActive} /> },
        ]}
        rowActions={(s) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(s.id)} />
            {s.isActive && <Button label="Pasife al" size="small" text severity="danger" onClick={() => deactivate(s)} />}
          </>
        )}
        footnote="Öğrenciler silinmez; pasife alınır. Veli bağı, atama ve finans öğrenci detayındadır."
      />
      <StudentFormDialog visible={dlg.isNew || Boolean(dlg.editId)} editId={dlg.editId} fromList={fromList} onHide={dlg.close} />
    </>
  );
}

/** Öğrenci yeni/düzenle dialogu (detay ekranı da kullanır). */
export function StudentFormDialog({ visible, editId, fromList, onHide }) {
  const { record, isLoading, error } = useRecord(studentKeys.detail(), studentsService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Öğrenciyi düzenle" isLoading={isLoading} error={error}>
      <StudentForm key={editId ?? "new"} record={editId ? record : null} editId={editId} onHide={onHide} />
    </RecordGate>
  );
}

export function StudentForm({ record, editId, onHide }) {
  const isEdit = Boolean(editId);
  const [serverError, setServerError] = useState(null);
  const schools = useLookup(["schools"], () => schoolsService.list({ size: 100, sort: "name,asc" }));
  const schoolOptions = schools.items
    .filter((s) => s.isActive || String(s.id) === String(record?.schoolId))
    .map((s) => ({ value: String(s.id), label: s.isActive ? s.name : `${s.name} (pasif)` }));
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(studentSchema), defaultValues: studentToForm(record) });
  const save = useEntityMutation((body) => (isEdit ? studentsService.update(editId, body) : studentsService.create(body)), STUDENT_INVALIDATE);

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await save.mutateAsync(studentToBody(v));
      toastSuccess(isEdit ? "Öğrenci güncellendi" : "Öğrenci eklendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError));
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title={isEdit ? "Öğrenciyi düzenle" : "Yeni öğrenci"}
      subtitle={isEdit ? record?.fullName : undefined}
      icon={isEdit ? "pi-pencil" : "pi-id-card"}
      tone={isEdit ? "info" : "ok"}
      onSubmit={onSubmit}
      submitting={save.isPending}
      formId="student-form"
      wide
    >
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Ad soyad" error={errors.fullName?.message} required>
          <InputText {...register("fullName")} />
        </Field>
        <Field label="Doğum tarihi" error={errors.birthDate?.message} required>
          <InputText type="date" max={todayISO()} {...register("birthDate")} />
        </Field>
        <Field label="T.C. kimlik" error={errors.tcNo?.message} help="İsteğe bağlı, 11 hane; sistemde benzersizdir.">
          <InputText inputMode="numeric" maxLength={11} className="mono" {...register("tcNo")} />
        </Field>
        <Field label="Okul" error={errors.schoolId?.message} htmlFor="student-school">
          <Controller
            name="schoolId"
            control={control}
            render={({ field }) => (
              <Dropdown
                inputId="student-school"
                value={field.value}
                options={schoolOptions}
                placeholder={schools.query.isPending ? "Yükleniyor…" : "Okul seçin"}
                filter
                showClear
                emptyMessage="Aktif okul yok"
                onChange={(e) => field.onChange(e.value ?? "")}
              />
            )}
          />
        </Field>
        <Field label="Sınıf" error={errors.grade?.message}>
          <InputText placeholder="Örn. 4-B" {...register("grade")} />
        </Field>
      </FormGrid>
    </AppDialog>
  );
}
