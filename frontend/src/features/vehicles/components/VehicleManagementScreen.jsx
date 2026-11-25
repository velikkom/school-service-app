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
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import VehiclePlate from "@/components/ui/VehiclePlate";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import { ActiveFilter, SearchFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { toList } from "@/lib/http/page";
import { vehiclesService } from "@/features/vehicles/service/vehiclesService";
import { VEHICLE_INVALIDATE, vehicleKeys } from "@/features/vehicles/hooks/keys";
import { VEHICLE_DEFAULTS, normalizePlate, vehicleSchema } from "@/features/vehicles/schemas/vehicleSchema";

/** /araclar (ADMIN) — araç listesi, yeni/düzenle dialogu, pasife alma. Plaka benzersiz (409). */
export default function VehicleManagementScreen() {
  return (
    <>
      <PageMeta title="Araçlar" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <VehicleList />
      </Suspense>
    </>
  );
}

function VehicleList() {
  const list = useListState({ q: "", isActive: "" });
  const query = useListQuery(vehicleKeys.list(), vehiclesService.list, list.params);
  const dlg = useDialogParams();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;
  const toggle = useEntityMutation(({ id, isActive }) => vehiclesService.update(id, { isActive }), VEHICLE_INVALIDATE);

  const setActive = (row, isActive) =>
    runConfirmed(
      isActive
        ? { title: "Aracı aktifleştir", message: `${row.plate} yeniden sefere atanabilir.`, acceptLabel: "Aktifleştir", tone: "info", success: "Araç aktifleştirildi" }
        : {
            title: "Aracı pasife al",
            message: `${row.plate} silinmez; listelerde pasif görünür ve yeni seferlerde seçilemez.`,
            acceptLabel: "Pasife al",
            success: "Araç pasife alındı",
          },
      () => toggle.mutateAsync({ id: row.id, isActive })
    );

  return (
    <>
      <ManagementList
        ariaLabel="Araçlar"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={
          <>
            <SearchFilter label="Plaka ara" placeholder="Örn. 34 ABC 123" value={list.filters.q} onChange={(v) => list.setFilter("q", v)} />
            <ActiveFilter value={list.filters.isActive} onChange={(v) => list.setFilter("isActive", v)} />
          </>
        }
        actions={<Button label="Yeni araç" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-car"
        emptyMessage="İlk aracı “Yeni araç” ile ekleyin."
        emptyAction={<Button label="Yeni araç" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          { key: "plate", header: "Plaka", main: true, body: (v) => <VehiclePlate plate={v.plate} /> },
          { key: "model", header: "Model", body: (v) => <MainCell title={v.model || "Model belirtilmedi"} /> },
          { key: "capacity", header: "Kapasite", body: (v) => <span className="mono">{v.capacity} kişi</span> },
          { key: "status", header: "Durum", body: (v) => <ActiveBadge value={v.isActive} /> },
        ]}
        rowActions={(v) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(v.id)} />
            {v.isActive ? (
              <Button label="Pasife al" size="small" text severity="danger" onClick={() => setActive(v, false)} />
            ) : (
              <Button label="Aktifleştir" size="small" text onClick={() => setActive(v, true)} />
            )}
          </>
        )}
        footnote="Araçlar silinmez; kullanım dışı araçları pasife alın. Plaka sistemde benzersizdir."
      />
      <VehicleFormDialog visible={dlg.isNew || Boolean(dlg.editId)} editId={dlg.editId} fromList={fromList} onHide={dlg.close} />
    </>
  );
}

function VehicleFormDialog({ visible, editId, fromList, onHide }) {
  const { record, isLoading, error } = useRecord(vehicleKeys.detail(), vehiclesService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Aracı düzenle" isLoading={isLoading} error={error}>
      <VehicleForm key={editId ?? "new"} record={editId ? record : null} editId={editId} onHide={onHide} />
    </RecordGate>
  );
}

export function VehicleForm({ record, editId, onHide }) {
  const isEdit = Boolean(editId);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(vehicleSchema),
    defaultValues: record
      ? { plate: record.plate ?? "", capacity: record.capacity != null ? String(record.capacity) : "", model: record.model ?? "", isActive: Boolean(record.isActive) }
      : VEHICLE_DEFAULTS,
  });
  const save = useEntityMutation((body) => (isEdit ? vehiclesService.update(editId, body) : vehiclesService.create(body)), VEHICLE_INVALIDATE);

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    const body = { plate: normalizePlate(v.plate), capacity: Number(v.capacity), model: v.model || undefined };
    if (isEdit) body.isActive = v.isActive;
    try {
      await save.mutateAsync(body);
      toastSuccess(isEdit ? "Araç güncellendi" : "Araç eklendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError));
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title={isEdit ? "Aracı düzenle" : "Yeni araç"}
      subtitle={isEdit ? record?.plate : undefined}
      icon={isEdit ? "pi-pencil" : "pi-car"}
      tone={isEdit ? "info" : "ok"}
      onSubmit={onSubmit}
      submitting={save.isPending}
      formId="vehicle-form"
    >
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Plaka" error={errors.plate?.message} required help="Boşluk ve büyük/küçük harf düzenlenir; plaka benzersizdir.">
          <InputText autoCapitalize="characters" {...register("plate")} />
        </Field>
        <Field label="Kapasite (kişi)" error={errors.capacity?.message} required>
          <InputText inputMode="numeric" {...register("capacity")} />
        </Field>
        <Field label="Model" error={errors.model?.message}>
          <InputText {...register("model")} />
        </Field>
        {isEdit && (
          <Field label="Aktif" htmlFor="vehicle-active" help="Pasif araç yeni seferlerde seçilemez.">
            <Controller
              name="isActive"
              control={control}
              render={({ field }) => <InputSwitch inputId="vehicle-active" checked={field.value} onChange={(e) => field.onChange(e.value)} />}
            />
          </Field>
        )}
      </FormGrid>
    </AppDialog>
  );
}
