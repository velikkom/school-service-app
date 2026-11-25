"use client";

import { Suspense, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { InputSwitch } from "primereact/inputswitch";
import { SelectButton } from "primereact/selectbutton";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import { ActiveFilter, SearchFilter, SelectFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { optionsOf } from "@/lib/formatting/labels";
import { toList } from "@/lib/http/page";
import { routesService } from "@/features/routes/service/routesService";
import { ROUTE_INVALIDATE, routeKeys } from "@/features/routes/hooks/keys";
import { routeSchema } from "@/features/routes/schemas/routeSchema";

const DIRECTION_OPTIONS = optionsOf("direction");

/** /guzergahlar (ADMIN) — güzergâh listesi + yeni/düzenle dialogu; duraklar detay ekranında. */
export default function RouteManagementScreen() {
  return (
    <>
      <PageMeta title="Güzergâhlar ve Duraklar" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <RouteList />
      </Suspense>
    </>
  );
}

function RouteList() {
  const list = useListState({ q: "", direction: "", isActive: "" });
  const query = useListQuery(routeKeys.list(), routesService.list, list.params);
  const dlg = useDialogParams();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;
  const toggle = useEntityMutation(({ id, isActive }) => routesService.update(id, { isActive }), ROUTE_INVALIDATE);

  const setActive = (row, isActive) =>
    runConfirmed(
      isActive
        ? { title: "Güzergâhı aktifleştir", message: `${row.name} yeniden kullanılabilir olacak.`, acceptLabel: "Aktifleştir", tone: "info", success: "Güzergâh aktifleştirildi" }
        : {
            title: "Güzergâhı pasife al",
            message: `${row.name} silinmez; pasif güzergâh yeni sefer ve atamalarda seçilemez.`,
            acceptLabel: "Pasife al",
            success: "Güzergâh pasife alındı",
          },
      () => toggle.mutateAsync({ id: row.id, isActive })
    );

  return (
    <>
      <ManagementList
        ariaLabel="Güzergâhlar"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={
          <>
            <SearchFilter label="Ara" placeholder="Güzergâh adı" value={list.filters.q} onChange={(v) => list.setFilter("q", v)} />
            <SelectFilter label="Yön" value={list.filters.direction} onChange={(v) => list.setFilter("direction", v)} options={DIRECTION_OPTIONS} />
            <ActiveFilter value={list.filters.isActive} onChange={(v) => list.setFilter("isActive", v)} />
          </>
        }
        actions={<Button label="Yeni güzergâh" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-directions"
        emptyMessage="İlk güzergâhı “Yeni güzergâh” ile ekleyin; duraklar güzergâh detayında eklenir."
        emptyAction={<Button label="Yeni güzergâh" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          { key: "name", header: "Güzergâh", main: true, body: (r) => <MainCell title={r.name} href={`/guzergahlar/${r.id}`} sub="Durakları görüntüle" /> },
          { key: "direction", header: "Yön", body: (r) => <StatusBadge group="direction" value={r.direction} /> },
          { key: "status", header: "Durum", body: (r) => <ActiveBadge value={r.isActive} /> },
        ]}
        rowActions={(r) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(r.id)} />
            {r.isActive ? (
              <Button label="Pasife al" size="small" text severity="danger" onClick={() => setActive(r, false)} />
            ) : (
              <Button label="Aktifleştir" size="small" text onClick={() => setActive(r, true)} />
            )}
          </>
        )}
        footnote="Güzergâhlar silinmez; pasife alınır. Durak ekleme, düzenleme ve silme güzergâh detayındadır."
      />
      <RouteFormDialog visible={dlg.isNew || Boolean(dlg.editId)} editId={dlg.editId} fromList={fromList} onHide={dlg.close} />
    </>
  );
}

/** Güzergâh yeni/düzenle dialogu (detay ekranı da kullanır). */
export function RouteFormDialog({ visible, editId, fromList, onHide }) {
  const { record, isLoading, error } = useRecord(routeKeys.detail(), routesService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Güzergâhı düzenle" isLoading={isLoading} error={error}>
      <RouteForm key={editId ?? "new"} record={editId ? record : null} editId={editId} onHide={onHide} />
    </RecordGate>
  );
}

function RouteForm({ record, editId, onHide }) {
  const isEdit = Boolean(editId);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(routeSchema),
    defaultValues: record
      ? { name: record.name ?? "", direction: record.direction ?? "MORNING", isActive: Boolean(record.isActive) }
      : { name: "", direction: "MORNING", isActive: true },
  });
  const save = useEntityMutation((body) => (isEdit ? routesService.update(editId, body) : routesService.create(body)), ROUTE_INVALIDATE);

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    const body = { name: v.name, direction: v.direction };
    if (isEdit) body.isActive = v.isActive;
    try {
      await save.mutateAsync(body);
      toastSuccess(isEdit ? "Güzergâh güncellendi" : "Güzergâh eklendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError));
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title={isEdit ? "Güzergâhı düzenle" : "Yeni güzergâh"}
      subtitle={isEdit ? record?.name : undefined}
      icon={isEdit ? "pi-pencil" : "pi-directions"}
      tone={isEdit ? "info" : "ok"}
      onSubmit={onSubmit}
      submitting={save.isPending}
      formId="route-form"
    >
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      {isEdit && <Notice tone="warn">Yön değişikliği, bu güzergâha bağlı atamaların yönüyle uyumsuzluk yaratabilir.</Notice>}
      <FormGrid>
        <Field label="Güzergâh adı" error={errors.name?.message} required full>
          <InputText {...register("name")} />
        </Field>
        <Field label="Yön" error={errors.direction?.message} required htmlFor="route-direction">
          <Controller
            name="direction"
            control={control}
            render={({ field }) => (
              <SelectButton
                id="route-direction"
                value={field.value}
                options={DIRECTION_OPTIONS}
                optionLabel="label"
                optionValue="value"
                allowEmpty={false}
                onChange={(e) => field.onChange(e.value)}
              />
            )}
          />
        </Field>
        {isEdit && (
          <Field label="Aktif" htmlFor="route-active">
            <Controller
              name="isActive"
              control={control}
              render={({ field }) => <InputSwitch inputId="route-active" checked={field.value} onChange={(e) => field.onChange(e.value)} />}
            />
          </Field>
        )}
      </FormGrid>
    </AppDialog>
  );
}
