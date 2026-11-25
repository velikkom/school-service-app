"use client";

import { Suspense, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import { useDialogParams } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { formatTime } from "@/lib/formatting/date";
import { toList } from "@/lib/http/page";
import { routesService } from "@/features/routes/service/routesService";
import { ROUTE_INVALIDATE, routeKeys } from "@/features/routes/hooks/keys";
import { stopSchema } from "@/features/routes/schemas/routeSchema";
import { RouteFormDialog } from "./RouteManagementScreen";
import styles from "../style/routes.module.scss";

/** /guzergahlar/[id] (ADMIN) — güzergâh bilgisi + duraklar (ekle/düzenle/sil). Durak koordinatı yok → harita yok. */
export default function RouteDetailScreen({ id }) {
  return (
    <Suspense fallback={<ScreenState kind="loading" />}>
      <RouteDetail id={id} />
    </Suspense>
  );
}

function RouteDetail({ id }) {
  const routeQuery = useQuery({ queryKey: [...routeKeys.detail(), String(id)], queryFn: () => routesService.get(id) });
  const dlg = useDialogParams();
  const route = routeQuery.data;

  return (
    <>
      <PageMeta title={route?.name ?? "Güzergâh"} crumb="Yönetim / Güzergâhlar" back="/guzergahlar" />
      <QueryState query={routeQuery} forbiddenHome="/guzergahlar">
        {(r) => (
          <div className="stack-lg">
            <section className={styles.head}>
              <div className={styles.headText}>
                <h2 className={styles.headTitle}>{r.name}</h2>
                <div className="row">
                  <StatusBadge group="direction" value={r.direction} />
                  <ActiveBadge value={r.isActive} />
                </div>
              </div>
              <Button label="Düzenle" icon="pi pi-pencil" severity="secondary" outlined onClick={() => dlg.openEdit(r.id)} />
            </section>
            <StopsPanel route={r} />
          </div>
        )}
      </QueryState>
      <RouteFormDialog visible={Boolean(dlg.editId)} editId={dlg.editId} fromList={route && String(route.id) === dlg.editId ? route : undefined} onHide={dlg.close} />
    </>
  );
}

function StopsPanel({ route }) {
  const stopsQuery = useQuery({ queryKey: routeKeys.stops(route.id), queryFn: () => routesService.stops(route.id) });
  const [editing, setEditing] = useState(null); // null kapalı, {} yeni, stop düzenle
  const stops = useMemo(() => [...toList(stopsQuery.data)].sort((a, b) => a.sequenceNo - b.sequenceNo), [stopsQuery.data]);
  const nextSeq = stops.length ? Math.max(...stops.map((s) => s.sequenceNo)) + 1 : 1;
  const del = useEntityMutation((stopId) => routesService.deleteStop(route.id, stopId), [routeKeys.stops(route.id), ...ROUTE_INVALIDATE]);

  const remove = (s) =>
    runConfirmed(
      {
        title: "Durağı sil",
        message: `“${s.name}” kalıcı olarak silinecek. Bu, sistemdeki tek gerçek silme işlemidir; durağa bağlı öğrenci ataması varsa silinemez (önce atamaları değiştirin).`,
        acceptLabel: "Sil",
        success: "Durak silindi",
        failure: "Durak silinemedi",
      },
      () => del.mutateAsync(s.id)
    );

  const query = { ...stopsQuery, data: stops };

  return (
    <>
      <ManagementList
        title="Duraklar"
        subtitle={`${stops.length} durak · sıra numarası güzergâh içinde benzersizdir`}
        ariaLabel="Duraklar"
        query={query}
        paginated={false}
        actions={<Button label="Durak ekle" icon="pi pi-plus" onClick={() => setEditing({})} />}
        emptyIcon="pi-map-marker"
        emptyTitle="Bu güzergâhta durak yok"
        emptyMessage="Öğrenci ataması yapabilmek için önce durak ekleyin."
        emptyAction={<Button label="Durak ekle" icon="pi pi-plus" onClick={() => setEditing({})} />}
        columns={[
          { key: "seq", header: "Sıra", body: (s) => <span className={styles.seq}>{s.sequenceNo}</span> },
          { key: "name", header: "Durak", main: true, body: (s) => <MainCell title={s.name} sub={`${s.sequenceNo}. durak · ${formatTime(s.plannedTime)}`} /> },
          { key: "time", header: "Planlanan saat", body: (s) => <span className="mono">{formatTime(s.plannedTime)}</span> },
        ]}
        rowActions={(s) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => setEditing(s)} />
            <Button label="Sil" icon="pi pi-trash" size="small" text severity="danger" onClick={() => remove(s)} />
          </>
        )}
        footnote="Durak verisinde koordinat tutulmadığı için harita gösterilmez."
      />
      {editing && <StopFormDialog routeId={route.id} stop={editing.id ? editing : null} nextSeq={nextSeq} onHide={() => setEditing(null)} />}
    </>
  );
}

function StopFormDialog({ routeId, stop, nextSeq, onHide }) {
  const isEdit = Boolean(stop);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(stopSchema),
    defaultValues: stop
      ? { name: stop.name ?? "", sequenceNo: String(stop.sequenceNo ?? ""), plannedTime: formatTime(stop.plannedTime).replace("—", "") }
      : { name: "", sequenceNo: String(nextSeq), plannedTime: "" },
  });
  const save = useEntityMutation(
    (body) => (isEdit ? routesService.updateStop(routeId, stop.id, body) : routesService.createStop(routeId, body)),
    [routeKeys.stops(routeId)]
  );

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    const body = { name: v.name, sequenceNo: Number(v.sequenceNo), plannedTime: v.plannedTime.slice(0, 5) };
    try {
      await save.mutateAsync(body);
      toastSuccess(isEdit ? "Durak güncellendi" : "Durak eklendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError, "Durak kaydedilemedi."));
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title={isEdit ? "Durağı düzenle" : "Durak ekle"}
      subtitle={isEdit ? stop.name : undefined}
      icon={isEdit ? "pi-pencil" : "pi-map-marker"}
      tone={isEdit ? "info" : "ok"}
      onSubmit={onSubmit}
      submitting={save.isPending}
      formId="stop-form"
    >
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Durak adı" error={errors.name?.message} required full>
          <InputText {...register("name")} />
        </Field>
        <Field label="Sıra no" error={errors.sequenceNo?.message} required help="Aynı güzergâhta tekrar edemez.">
          <InputText inputMode="numeric" {...register("sequenceNo")} />
        </Field>
        <Field label="Planlanan saat" error={errors.plannedTime?.message} required>
          <InputText type="time" {...register("plannedTime")} />
        </Field>
      </FormGrid>
    </AppDialog>
  );
}
