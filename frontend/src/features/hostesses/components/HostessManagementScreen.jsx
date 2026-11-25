"use client";

import { Suspense, useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { InputSwitch } from "primereact/inputswitch";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import AccountPicker, { TemporaryPasswordNotice } from "@/components/data/AccountPicker";
import { ActiveFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { toList } from "@/lib/http/page";
import { hostessesService } from "@/features/hostesses/service/hostessesService";
import { usersService } from "@/features/users/service/usersService";
import { HOSTESS_INVALIDATE, hostessKeys } from "@/features/hostesses/hooks/keys";
import { hostessSchema } from "@/features/hostesses/schemas/hostessSchema";

/** /hostesler (ADMIN) — hostes profilleri; ad/telefon kullanıcı hesabından eşlenir (HostessResponse ad içermez). */
export default function HostessManagementScreen() {
  return (
    <>
      <PageMeta title="Hostesler" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <HostessList />
      </Suspense>
    </>
  );
}

function HostessList() {
  const list = useListState({ isActive: "" });
  const query = useListQuery(hostessKeys.list(), hostessesService.list, list.params);
  const users = useLookup(["users", "HOSTESS"], () => usersService.list({ role: "HOSTESS", size: 100, sort: "fullName,asc" }));
  const all = useLookup(["hostesses"], () => hostessesService.list({ size: 100 }));
  const dlg = useDialogParams();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;
  const toggle = useEntityMutation(({ id, isActive }) => hostessesService.update(id, { isActive }), HOSTESS_INVALIDATE);
  const nameOf = (h) => users.byId.get(String(h.userId))?.fullName ?? `Kullanıcı #${h.userId}`;

  const setActive = (row, isActive) =>
    runConfirmed(
      isActive
        ? { title: "Hostesi aktifleştir", message: `${nameOf(row)} yeniden sefere atanabilir.`, acceptLabel: "Aktifleştir", tone: "info", success: "Hostes aktifleştirildi" }
        : { title: "Hostesi pasife al", message: `${nameOf(row)} silinmez; yeni seferlerde seçilemez.`, acceptLabel: "Pasife al", success: "Hostes pasife alındı" },
      () => toggle.mutateAsync({ id: row.id, isActive })
    );

  return (
    <>
      <ManagementList
        ariaLabel="Hostesler"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={<ActiveFilter value={list.filters.isActive} onChange={(v) => list.setFilter("isActive", v)} />}
        actions={<Button label="Yeni hostes" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-user"
        emptyMessage="İlk hostesi “Yeni hostes” ile ekleyin."
        emptyAction={<Button label="Yeni hostes" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          {
            key: "name",
            header: "Hostes",
            main: true,
            body: (h) => <MainCell title={nameOf(h)} sub={users.byId.get(String(h.userId))?.email} href={`/kullanicilar/${h.userId}`} />,
          },
          { key: "phone", header: "Telefon", body: (h) => users.byId.get(String(h.userId))?.phone || "—" },
          { key: "status", header: "Durum", body: (h) => <ActiveBadge value={h.isActive} /> },
        ]}
        rowActions={(h) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(h.id)} />
            {h.isActive ? (
              <Button label="Pasife al" size="small" text severity="danger" onClick={() => setActive(h, false)} />
            ) : (
              <Button label="Aktifleştir" size="small" text onClick={() => setActive(h, true)} />
            )}
          </>
        )}
        footnote="Ad ve telefon kullanıcı hesabından gelir (ilk 100 hostes hesabı eşlenir). Hostesler silinmez; pasife alınır."
      />
      <HostessFormDialog
        visible={dlg.isNew || Boolean(dlg.editId)}
        editId={dlg.editId}
        fromList={fromList}
        onHide={dlg.close}
        users={users}
        takenUserIds={all.items.map((r) => String(r.userId))}
        nameOf={nameOf}
      />
    </>
  );
}

function HostessFormDialog({ visible, editId, fromList, onHide, users, takenUserIds, nameOf }) {
  const { record, isLoading, error } = useRecord(hostessKeys.detail(), hostessesService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Hostesi düzenle" isLoading={isLoading} error={error}>
      {editId ? (
        <HostessEditForm key={editId} record={record} onHide={onHide} name={record ? nameOf(record) : ""} />
      ) : (
        <HostessCreateForm onHide={onHide} users={users} takenUserIds={takenUserIds} />
      )}
    </RecordGate>
  );
}

function HostessCreateForm({ onHide, users, takenUserIds }) {
  const qc = useQueryClient();
  const [serverError, setServerError] = useState(null);
  const [created, setCreated] = useState(null);
  const [createdUserId, setCreatedUserId] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    formState: { errors },
  } = useForm({ resolver: zodResolver(hostessSchema), defaultValues: { accountMode: "existing", userId: "", fullName: "", email: "", phone: "" } });
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
        const res = await usersService.create({ fullName: v.fullName, email: v.email, phone: v.phone || undefined, role: "HOSTESS" });
        userId = String(res.user.id);
        tempPassword = res.temporaryPassword;
        setCreatedUserId(userId);
        await qc.invalidateQueries({ queryKey: ["lookup", "users"] });
      }
      await hostessesService.create({ userId: Number(userId) });
      await Promise.all(HOSTESS_INVALIDATE.map((queryKey) => qc.invalidateQueries({ queryKey })));
      toastSuccess("Hostes eklendi");
      if (tempPassword) setCreated({ email: v.email, password: tempPassword });
      else onHide();
    } catch (err) {
      if (userId && v.accountMode === "new") {
        setValue("accountMode", "existing");
        setValue("userId", userId);
        if (tempPassword) setCreated({ email: v.email, password: tempPassword, partial: true });
      }
      setServerError(formErrorText(err, setError, "Hostes eklenemedi."));
    } finally {
      setSubmitting(false);
    }
  });

  if (created && !created.partial) {
    return (
      <AppDialog visible onHide={onHide} title="Hostes eklendi" icon="pi-check" tone="ok" cancelLabel="Kapat">
        <TemporaryPasswordNotice email={created.email} password={created.password} />
      </AppDialog>
    );
  }

  return (
    <AppDialog visible onHide={onHide} title="Yeni hostes" icon="pi-user-plus" tone="ok" onSubmit={onSubmit} submitting={submitting} formId="hostess-form" wide>
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      {created?.partial && <TemporaryPasswordNotice email={created.email} password={created.password} />}
      <AccountPicker control={control} register={register} errors={errors} mode={mode} userOptions={options} loading={users.query.isPending} roleLabel="Hostes" idPrefix="hostess" />
    </AppDialog>
  );
}

function HostessEditForm({ record, onHide, name }) {
  const [serverError, setServerError] = useState(null);
  const { control, handleSubmit } = useForm({ defaultValues: { isActive: Boolean(record?.isActive) } });
  const save = useEntityMutation((body) => hostessesService.update(record.id, body), HOSTESS_INVALIDATE);
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await save.mutateAsync({ isActive: v.isActive });
      toastSuccess("Hostes güncellendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, () => {}));
    }
  });
  return (
    <AppDialog visible onHide={onHide} title="Hostesi düzenle" subtitle={name} icon="pi-pencil" tone="info" onSubmit={onSubmit} submitting={save.isPending} formId="hostess-edit-form">
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <Field label="Aktif" htmlFor="hostess-active" help="Pasif hostes yeni seferlerde seçilemez.">
        <Controller name="isActive" control={control} render={({ field }) => <InputSwitch inputId="hostess-active" checked={field.value} onChange={(e) => field.onChange(e.value)} />} />
      </Field>
      <p className="text-xs muted">Ad, e-posta ve telefon kullanıcı hesabından düzenlenir (Kullanıcılar).</p>
    </AppDialog>
  );
}
