"use client";

import { Suspense, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { SelectButton } from "primereact/selectbutton";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ManagementList, { MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import { SelectFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { formatDate, formatTime, todayISO } from "@/lib/formatting/date";
import { labelOf, optionsOf } from "@/lib/formatting/labels";
import { toList } from "@/lib/http/page";
import { assignmentsService } from "@/features/assignments/service/assignmentsService";
import { studentsService } from "@/features/students/service/studentsService";
import { routesService } from "@/features/routes/service/routesService";
import { ASSIGNMENT_INVALIDATE, assignmentKeys } from "@/features/assignments/hooks/keys";
import { assignmentSchema } from "@/features/assignments/schemas/assignmentSchema";

const DIRECTION_OPTIONS = optionsOf("direction");
const stopsKey = (routeId) => ["routes", "stops", String(routeId)];

function useStudents() {
  return useLookup(["students"], () => studentsService.list({ size: 100, sort: "fullName,asc" }), { label: (s) => s.fullName });
}
function useRoutes() {
  return useLookup(["routes"], () => routesService.list({ size: 100, sort: "name,asc" }), { label: (r) => r.name });
}

function StopLabel({ routeId, stopId }) {
  const q = useQuery({ queryKey: stopsKey(routeId), queryFn: () => routesService.stops(routeId), staleTime: 60_000 });
  const stop = toList(q.data).find((x) => String(x.id) === String(stopId));
  if (q.isPending) return <span className="muted">…</span>;
  return stop ? (
    <span>
      {stop.sequenceNo}. {stop.name} <span className="mono text-xs muted">{formatTime(stop.plannedTime)}</span>
    </span>
  ) : (
    <span className="muted">Durak #{stopId}</span>
  );
}

/** /atamalar (ADMIN) — öğrenci–güzergâh/durak atamaları. AssignmentResponse ad içermez; adlar listelerden eşlenir. */
export default function AssignmentManagementScreen() {
  return (
    <>
      <PageMeta title="Öğrenci Atamaları" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <AssignmentList />
      </Suspense>
    </>
  );
}

function AssignmentList() {
  const list = useListState({ studentId: "", routeId: "", direction: "" });
  const query = useListQuery(assignmentKeys.list(), assignmentsService.list, list.params);
  const students = useStudents();
  const routes = useRoutes();
  const dlg = useDialogParams();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;
  const presetStudent = dlg.get("ogrenci");

  return (
    <>
      <ManagementList
        ariaLabel="Öğrenci atamaları"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={
          <>
            <SelectFilter label="Öğrenci" value={list.filters.studentId} onChange={(v) => list.setFilter("studentId", v)} options={students.options} filter loading={students.query.isPending} />
            <SelectFilter label="Güzergâh" value={list.filters.routeId} onChange={(v) => list.setFilter("routeId", v)} options={routes.options} filter loading={routes.query.isPending} />
            <SelectFilter label="Yön" value={list.filters.direction} onChange={(v) => list.setFilter("direction", v)} options={DIRECTION_OPTIONS} />
          </>
        }
        actions={<Button label="Yeni atama" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-directions"
        emptyMessage="Öğrenciyi bir güzergâh ve durağa atamak için “Yeni atama” kullanın."
        emptyAction={<Button label="Yeni atama" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          {
            key: "student",
            header: "Öğrenci",
            main: true,
            body: (a) => (
              <MainCell title={students.byId.get(String(a.studentId))?.fullName ?? `Öğrenci #${a.studentId}`} sub={labelOf("direction", a.direction).label} href={`/ogrenciler/${a.studentId}?tab=atamalar`} />
            ),
          },
          { key: "route", header: "Güzergâh", body: (a) => routes.byId.get(String(a.routeId))?.name ?? `Güzergâh #${a.routeId}` },
          { key: "stop", header: "Durak", body: (a) => <StopLabel routeId={a.routeId} stopId={a.stopId} /> },
          { key: "valid", header: "Geçerlilik", body: (a) => `${formatDate(a.validFrom)} – ${a.validTo ? formatDate(a.validTo) : "süresiz"}` },
        ]}
        rowActions={(a) => <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(a.id)} />}
        footnote="Aynı öğrenci ve yönde tarihleri çakışan iki aktif atama olamaz. Atamayı bitirmek için bitiş tarihi girin."
      />
      <AssignmentFormDialog
        visible={dlg.isNew || Boolean(dlg.editId)}
        editId={dlg.editId}
        fromList={fromList}
        presetStudent={presetStudent}
        onHide={dlg.close}
        students={students}
        routes={routes}
      />
    </>
  );
}

function AssignmentFormDialog({ visible, editId, fromList, presetStudent, onHide, students, routes }) {
  const { record, isLoading, error } = useRecord(assignmentKeys.detail(), assignmentsService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Atamayı düzenle" isLoading={isLoading} error={error}>
      <AssignmentForm key={editId ?? "new"} record={editId ? record : null} editId={editId} presetStudent={presetStudent} onHide={onHide} students={students} routes={routes} />
    </RecordGate>
  );
}

export function AssignmentForm({ record, editId, presetStudent, onHide, students, routes }) {
  const isEdit = Boolean(editId);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(assignmentSchema),
    defaultValues: record
      ? {
          studentId: String(record.studentId),
          direction: record.direction,
          routeId: String(record.routeId),
          stopId: String(record.stopId),
          validFrom: record.validFrom ?? "",
          validTo: record.validTo ?? "",
        }
      : { studentId: presetStudent ?? "", direction: "MORNING", routeId: "", stopId: "", validFrom: todayISO(), validTo: "" },
  });
  const direction = useWatch({ control, name: "direction" });
  const routeId = useWatch({ control, name: "routeId" });
  const stopsQuery = useQuery({ queryKey: stopsKey(routeId), queryFn: () => routesService.stops(routeId), enabled: Boolean(routeId) });
  const stopOptions = [...toList(stopsQuery.data)]
    .sort((a, b) => a.sequenceNo - b.sequenceNo)
    .map((s) => ({ value: String(s.id), label: `${s.sequenceNo}. ${s.name} · ${formatTime(s.plannedTime)}` }));
  const routeOptions = routes.items
    .filter((r) => r.direction === direction && (r.isActive || String(r.id) === String(record?.routeId)))
    .map((r) => ({ value: String(r.id), label: r.name }));
  const studentOptions = students.items
    .filter((s) => s.isActive || String(s.id) === String(record?.studentId ?? presetStudent))
    .map((s) => ({ value: String(s.id), label: s.fullName }));

  const save = useEntityMutation((body) => (isEdit ? assignmentsService.update(editId, body) : assignmentsService.create(body)), ASSIGNMENT_INVALIDATE);

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    const body = {
      routeId: Number(v.routeId),
      stopId: Number(v.stopId),
      direction: v.direction,
      validFrom: v.validFrom,
      validTo: v.validTo || undefined,
    };
    if (!isEdit) body.studentId = Number(v.studentId);
    try {
      await save.mutateAsync(body);
      toastSuccess(isEdit ? "Atama güncellendi" : "Atama oluşturuldu");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError, "Atama kaydedilemedi."));
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title={isEdit ? "Atamayı düzenle" : "Yeni atama"}
      subtitle={isEdit ? students.byId.get(String(record?.studentId))?.fullName : undefined}
      icon={isEdit ? "pi-pencil" : "pi-directions"}
      tone={isEdit ? "info" : "ok"}
      onSubmit={onSubmit}
      submitting={save.isPending}
      formId="assignment-form"
      wide
    >
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Öğrenci" error={errors.studentId?.message} required htmlFor="as-student" help={isEdit ? "Öğrenci değiştirilemez." : undefined}>
          <Controller
            name="studentId"
            control={control}
            render={({ field }) => (
              <Dropdown
                inputId="as-student"
                value={field.value}
                options={studentOptions}
                disabled={isEdit}
                filter
                placeholder={students.query.isPending ? "Yükleniyor…" : "Öğrenci seçin"}
                emptyMessage="Öğrenci yok"
                onChange={(e) => field.onChange(e.value ?? "")}
              />
            )}
          />
        </Field>
        <Field label="Yön" error={errors.direction?.message} required htmlFor="as-direction">
          <Controller
            name="direction"
            control={control}
            render={({ field }) => (
              <SelectButton
                id="as-direction"
                value={field.value}
                options={DIRECTION_OPTIONS}
                optionLabel="label"
                optionValue="value"
                allowEmpty={false}
                onChange={(e) => {
                  field.onChange(e.value);
                  setValue("routeId", "");
                  setValue("stopId", "");
                }}
              />
            )}
          />
        </Field>
        <Field label="Güzergâh" error={errors.routeId?.message} required htmlFor="as-route" help="Yalnız seçilen yöndeki aktif güzergâhlar.">
          <Controller
            name="routeId"
            control={control}
            render={({ field }) => (
              <Dropdown
                inputId="as-route"
                value={field.value}
                options={routeOptions}
                filter
                placeholder="Güzergâh seçin"
                emptyMessage="Bu yönde güzergâh yok"
                onChange={(e) => {
                  field.onChange(e.value ?? "");
                  setValue("stopId", "");
                }}
              />
            )}
          />
        </Field>
        <Field label="Durak" error={errors.stopId?.message} required htmlFor="as-stop">
          <Controller
            name="stopId"
            control={control}
            render={({ field }) => (
              <Dropdown
                inputId="as-stop"
                value={field.value}
                options={stopOptions}
                disabled={!routeId}
                placeholder={!routeId ? "Önce güzergâh seçin" : stopsQuery.isPending ? "Yükleniyor…" : "Durak seçin"}
                emptyMessage="Bu güzergâhta durak yok"
                onChange={(e) => field.onChange(e.value ?? "")}
              />
            )}
          />
        </Field>
        <Field label="Başlangıç" error={errors.validFrom?.message} required>
          <InputText type="date" {...register("validFrom")} />
        </Field>
        <Field label="Bitiş" error={errors.validTo?.message} help="Boş = süresiz. Aynı öğrenci ve yönde tarihleri çakışan iki aktif atama olamaz.">
          <InputText type="date" {...register("validTo")} />
        </Field>
      </FormGrid>
    </AppDialog>
  );
}

