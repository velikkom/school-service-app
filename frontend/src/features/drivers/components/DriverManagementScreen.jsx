"use client";

import { Suspense, useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { InputSwitch } from "primereact/inputswitch";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import AccountPicker, { TemporaryPasswordNotice } from "@/components/data/AccountPicker";
import { ActiveFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { toList } from "@/lib/http/page";
import { driversService } from "@/features/drivers/service/driversService";
import { usersService } from "@/features/users/service/usersService";
import { DRIVER_INVALIDATE, driverKeys } from "@/features/drivers/hooks/keys";
import { driverSchema, driverUpdateSchema } from "@/features/drivers/schemas/driverSchema";

/** DRIVER rolündeki kullanıcılar (ad/e-posta eşlemesi için; DriverResponse ad içermez). */
function useDriverUsers() {
  return useLookup(["users", "DRIVER"], () => usersService.list({ role: "DRIVER", size: 100, sort: "fullName,asc" }));
}

/** /soforler (ADMIN) — şoför profilleri; ad/telefon kullanıcı hesabından eşlenir. */
export default function DriverManagementScreen() {
  return (
    <>
      <PageMeta title="Şoförler" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <DriverList />
      </Suspense>
    </>
  );
}

function DriverList() {
  const list = useListState({ isActive: "" });
  const query = useListQuery(driverKeys.list(), driversService.list, list.params);
  const users = useDriverUsers();
  const allDrivers = useLookup(["drivers"], () => driversService.list({ size: 100 }), { enabled: true });
  const dlg = useDialogParams();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;
  const toggle = useEntityMutation(({ id, isActive }) => driversService.update(id, { isActive }), DRIVER_INVALIDATE);
  const nameOf = (d) => users.byId.get(String(d.userId))?.fullName ?? `Kullanıcı #${d.userId}`;

  const setActive = (row, isActive) =>
    runConfirmed(
      isActive
        ? { title: "Şoförü aktifleştir", message: `${nameOf(row)} yeniden sefere atanabilir.`, acceptLabel: "Aktifleştir", tone: "info", success: "Şoför aktifleştirildi" }
        : {
            title: "Şoförü pasife al",
            message: `${nameOf(row)} silinmez; yeni seferlerde seçilemez. Kullanıcı hesabı ayrıca Kullanıcılar ekranından yönetilir.`,
            acceptLabel: "Pasife al",
            success: "Şoför pasife alındı",
          },
      () => toggle.mutateAsync({ id: row.id, isActive })
    );

  return (
    <>
      <ManagementList
        ariaLabel="Şoförler"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={<ActiveFilter value={list.filters.isActive} onChange={(v) => list.setFilter("isActive", v)} />}
        actions={<Button label="Yeni şoför" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-id-card"
        emptyMessage="İlk şoförü “Yeni şoför” ile ekleyin."
        emptyAction={<Button label="Yeni şoför" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          {
            key: "name",
            header: "Şoför",
            main: true,
            body: (d) => {
              const u = users.byId.get(String(d.userId));
              return <MainCell title={nameOf(d)} sub={u?.email} href={`/kullanicilar/${d.userId}`} />;
            },
          },
          { key: "phone", header: "Telefon", body: (d) => users.byId.get(String(d.userId))?.phone || "—" },
          { key: "license", header: "Ehliyet no", body: (d) => <span className="mono">{d.licenseNo || "—"}</span> },
          { key: "status", header: "Durum", body: (d) => <ActiveBadge value={d.isActive} /> },
        ]}
        rowActions={(d) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(d.id)} />
            {d.isActive ? (
              <Button label="Pasife al" size="small" text severity="danger" onClick={() => setActive(d, false)} />
            ) : (
              <Button label="Aktifleştir" size="small" text onClick={() => setActive(d, true)} />
            )}
          </>
        )}
        footnote="Ad ve telefon kullanıcı hesabından gelir (ilk 100 şoför hesabı eşlenir). Şoförler silinmez; pasife alınır."
      />
      <DriverFormDialog
        visible={dlg.isNew || Boolean(dlg.editId)}
        editId={dlg.editId}
        fromList={fromList}
        onHide={dlg.close}
        users={users}
        takenUserIds={allDrivers.items.map((r) => String(r.userId))}
      />
    </>
  );
}

function DriverFormDialog({ visible, editId, fromList, onHide, users, takenUserIds }) {
  const { record, isLoading, error } = useRecord(driverKeys.detail(), driversService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Şoförü düzenle" isLoading={isLoading} error={error}>
      {editId ? (
        <DriverEditForm key={editId} record={record} onHide={onHide} name={users.byId.get(String(record?.userId))?.fullName} />
      ) : (
        <DriverCreateForm onHide={onHide} users={users} takenUserIds={takenUserIds} />
      )}
    </RecordGate>
  );
}

function DriverCreateForm({ onHide, users, takenUserIds }) {
  const qc = useQueryClient();
  const [serverError, setServerError] = useState(null);
  const [created, setCreated] = useState(null); // { email, password }
  const [createdUserId, setCreatedUserId] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(driverSchema),
    defaultValues: { accountMode: "existing", userId: "", fullName: "", email: "", phone: "", licenseNo: "" },
  });
  const mode = useWatch({ control, name: "accountMode" });
  const options = useMemo(
    () => users.items.filter((u) => !takenUserIds.includes(String(u.id))).map((u) => ({ value: String(u.id), label: `${u.fullName} · ${u.email}` })),
    [users.items, takenUserIds]
  );

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    setSubmitting(true);
    let userId = v.accountMode === "existing" ? v.userId : createdUserId;
    let tempPassword = null;
    try {
      if (!userId) {
        const res = await usersService.create({ fullName: v.fullName, email: v.email, phone: v.phone || undefined, role: "DRIVER" });
        userId = String(res.user.id);
        tempPassword = res.temporaryPassword;
        setCreatedUserId(userId);
        await qc.invalidateQueries({ queryKey: ["lookup", "users"] });
      }
      await driversService.create({ userId: Number(userId), licenseNo: v.licenseNo });
      await Promise.all(DRIVER_INVALIDATE.map((queryKey) => qc.invalidateQueries({ queryKey })));
      toastSuccess("Şoför eklendi");
      if (tempPassword) setCreated({ email: v.email, password: tempPassword });
      else onHide();
    } catch (err) {
      if (userId && v.accountMode === "new") {
        // Hesap açıldı, profil adımı başarısız: tekrar denemede aynı hesap kullanılır.
        setValue("accountMode", "existing");
        setValue("userId", userId);
        if (tempPassword) setCreated({ email: v.email, password: tempPassword, partial: true });
      }
      setServerError(formErrorText(err, setError, "Şoför eklenemedi."));
    } finally {
      setSubmitting(false);
    }
  });

  if (created && !created.partial) {
    return (
      <AppDialog visible onHide={onHide} title="Şoför eklendi" icon="pi-check" tone="ok" cancelLabel="Kapat">
        <TemporaryPasswordNotice email={created.email} password={created.password} />
      </AppDialog>
    );
  }

  return (
    <AppDialog visible onHide={onHide} title="Yeni şoför" icon="pi-id-card" tone="ok" onSubmit={onSubmit} submitting={submitting} formId="driver-form" wide>
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      {created?.partial && <TemporaryPasswordNotice email={created.email} password={created.password} />}
      <AccountPicker
        control={control}
        register={register}
        errors={errors}
        mode={mode}
        userOptions={options}
        loading={users.query.isPending}
        roleLabel="Şoför"
        idPrefix="driver"
      />
      <Field label="Ehliyet no" error={errors.licenseNo?.message} required>
        <InputText className="mono" {...register("licenseNo")} />
      </Field>
    </AppDialog>
  );
}

function DriverEditForm({ record, onHide, name }) {
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(driverUpdateSchema),
    defaultValues: { licenseNo: record?.licenseNo ?? "", isActive: Boolean(record?.isActive) },
  });
  const save = useEntityMutation((body) => driversService.update(record.id, body), DRIVER_INVALIDATE);
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await save.mutateAsync({ licenseNo: v.licenseNo, isActive: v.isActive });
      toastSuccess("Şoför güncellendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError));
    }
  });
  return (
    <AppDialog visible onHide={onHide} title="Şoförü düzenle" subtitle={name} icon="pi-pencil" tone="info" onSubmit={onSubmit} submitting={save.isPending} formId="driver-edit-form">
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Ehliyet no" error={errors.licenseNo?.message} required>
          <InputText className="mono" {...register("licenseNo")} />
        </Field>
        <Field label="Aktif" htmlFor="driver-active">
          <Controller name="isActive" control={control} render={({ field }) => <InputSwitch inputId="driver-active" checked={field.value} onChange={(e) => field.onChange(e.value)} />} />
        </Field>
      </FormGrid>
      <p className="text-xs muted">Ad, e-posta ve telefon kullanıcı hesabından düzenlenir (Kullanıcılar).</p>
    </AppDialog>
  );
}
