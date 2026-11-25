"use client";

import { Suspense, useMemo, useState } from "react";
import Link from "next/link";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ManagementList, { MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import AccountPicker, { TemporaryPasswordNotice } from "@/components/data/AccountPicker";
import { SearchFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { toList } from "@/lib/http/page";
import { parentsService } from "@/features/parents/service/parentsService";
import { usersService } from "@/features/users/service/usersService";
import { PARENT_INVALIDATE, parentKeys } from "@/features/parents/hooks/keys";
import { parentSchema, parentUpdateSchema } from "@/features/parents/schemas/parentSchema";

/** PARENT rolündeki kullanıcılar (e-posta eşlemesi + hesap seçimi). */
export function useParentUsers() {
  return useLookup(["users", "PARENT"], () => usersService.list({ role: "PARENT", size: 100, sort: "fullName,asc" }));
}

/** /veliler (ADMIN) — veli profilleri; detayda bağlı öğrenciler. */
export default function ParentManagementScreen() {
  return (
    <>
      <PageMeta title="Veliler" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <ParentList />
      </Suspense>
    </>
  );
}

function ParentList() {
  const list = useListState({ q: "" });
  const query = useListQuery(parentKeys.list(), parentsService.list, list.params);
  const users = useParentUsers();
  const all = useLookup(["parents"], () => parentsService.list({ size: 100, sort: "fullName,asc" }));
  const dlg = useDialogParams();
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;

  return (
    <>
      <ManagementList
        ariaLabel="Veliler"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={<SearchFilter label="Ara" placeholder="Veli adı" value={list.filters.q} onChange={(v) => list.setFilter("q", v)} />}
        actions={<Button label="Yeni veli" icon="pi pi-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-users"
        emptyMessage="İlk veliyi “Yeni veli” ile ekleyin; öğrenci bağı veli veya öğrenci detayından kurulur."
        emptyAction={<Button label="Yeni veli" icon="pi pi-plus" onClick={dlg.openNew} />}
        columns={[
          { key: "name", header: "Veli", main: true, body: (p) => <MainCell title={p.fullName} sub={users.byId.get(String(p.userId))?.email} href={`/veliler/${p.id}`} /> },
          { key: "phone", header: "Telefon", body: (p) => p.phone || "—" },
          { key: "tc", header: "T.C. kimlik", body: (p) => <span className="mono">{p.tcNo || "—"}</span> },
        ]}
        rowActions={(p) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(p.id)} />
            <Link href={`/veliler/${p.id}`} className="p-button p-button-text p-button-sm">
              Bağlı öğrenciler
            </Link>
          </>
        )}
        footnote="Veli profilleri silinmez; hesabı kapatmak için Kullanıcılar ekranından pasife alın."
      />
      <ParentFormDialog
        visible={dlg.isNew || Boolean(dlg.editId)}
        editId={dlg.editId}
        fromList={fromList}
        onHide={dlg.close}
        users={users}
        takenUserIds={all.items.map((p) => String(p.userId))}
      />
    </>
  );
}

/** Veli yeni/düzenle dialogu (detay ekranı da kullanır). */
export function ParentFormDialog({ visible, editId, fromList, onHide, users, takenUserIds = [] }) {
  const { record, isLoading, error } = useRecord(parentKeys.detail(), parentsService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Veliyi düzenle" isLoading={isLoading} error={error}>
      {editId ? <ParentEditForm key={editId} record={record} onHide={onHide} /> : <ParentCreateForm onHide={onHide} users={users} takenUserIds={takenUserIds} />}
    </RecordGate>
  );
}

function ParentCreateForm({ onHide, users, takenUserIds }) {
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
  } = useForm({
    resolver: zodResolver(parentSchema),
    defaultValues: { accountMode: "existing", userId: "", email: "", fullName: "", phone: "", tcNo: "" },
  });
  const mode = useWatch({ control, name: "accountMode" });
  const userId = useWatch({ control, name: "userId" });
  const options = useMemo(
    () => users.items.filter((u) => !takenUserIds.includes(String(u.id))).map((u) => ({ value: String(u.id), label: `${u.fullName} · ${u.email}` })),
    [users.items, takenUserIds]
  );
  const selectedUser = users.byId.get(String(userId));

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    setSubmitting(true);
    let uid = v.accountMode === "existing" ? v.userId : createdUserId;
    let tempPassword = null;
    try {
      if (!uid) {
        const res = await usersService.create({ fullName: v.fullName, email: v.email, phone: v.phone || undefined, role: "PARENT" });
        uid = String(res.user.id);
        tempPassword = res.temporaryPassword;
        setCreatedUserId(uid);
        await qc.invalidateQueries({ queryKey: ["lookup", "users"] });
      }
      await parentsService.create({ userId: Number(uid), fullName: v.fullName, phone: v.phone || undefined, tcNo: v.tcNo || undefined });
      await Promise.all(PARENT_INVALIDATE.map((queryKey) => qc.invalidateQueries({ queryKey })));
      toastSuccess("Veli eklendi");
      if (tempPassword) setCreated({ email: v.email, password: tempPassword });
      else onHide();
    } catch (err) {
      if (uid && v.accountMode === "new") {
        setValue("accountMode", "existing");
        setValue("userId", uid);
        if (tempPassword) setCreated({ email: v.email, password: tempPassword, partial: true });
      }
      setServerError(formErrorText(err, setError, "Veli eklenemedi."));
    } finally {
      setSubmitting(false);
    }
  });

  if (created && !created.partial) {
    return (
      <AppDialog visible onHide={onHide} title="Veli eklendi" icon="pi-check" tone="ok" cancelLabel="Kapat">
        <TemporaryPasswordNotice email={created.email} password={created.password} />
      </AppDialog>
    );
  }

  return (
    <AppDialog visible onHide={onHide} title="Yeni veli" icon="pi-users" tone="ok" onSubmit={onSubmit} submitting={submitting} formId="parent-form" wide>
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      {created?.partial && <TemporaryPasswordNotice email={created.email} password={created.password} />}
      <AccountPicker
        control={control}
        register={register}
        errors={errors}
        mode={mode}
        userOptions={options}
        loading={users.query.isPending}
        roleLabel="Veli"
        showName={false}
        idPrefix="parent"
      />
      <FormGrid>
        <Field label="Ad soyad" error={errors.fullName?.message} required help={mode === "existing" && selectedUser ? `Hesap adı: ${selectedUser.fullName}` : undefined}>
          <InputText {...register("fullName")} />
        </Field>
        <Field label="Telefon" error={errors.phone?.message}>
          <InputText type="tel" inputMode="tel" {...register("phone")} />
        </Field>
        <Field label="T.C. kimlik" error={errors.tcNo?.message}>
          <InputText inputMode="numeric" maxLength={11} className="mono" {...register("tcNo")} />
        </Field>
      </FormGrid>
    </AppDialog>
  );
}

function ParentEditForm({ record, onHide }) {
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(parentUpdateSchema),
    defaultValues: { fullName: record?.fullName ?? "", phone: record?.phone ?? "", tcNo: record?.tcNo ?? "" },
  });
  const save = useEntityMutation((body) => parentsService.update(record.id, body), PARENT_INVALIDATE);
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await save.mutateAsync({ fullName: v.fullName, phone: v.phone || undefined, tcNo: v.tcNo || undefined });
      toastSuccess("Veli güncellendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError));
    }
  });
  return (
    <AppDialog visible onHide={onHide} title="Veliyi düzenle" subtitle={record?.fullName} icon="pi-pencil" tone="info" onSubmit={onSubmit} submitting={save.isPending} formId="parent-edit-form">
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Ad soyad" error={errors.fullName?.message} required full>
          <InputText {...register("fullName")} />
        </Field>
        <Field label="Telefon" error={errors.phone?.message}>
          <InputText type="tel" inputMode="tel" {...register("phone")} />
        </Field>
        <Field label="T.C. kimlik" error={errors.tcNo?.message}>
          <InputText inputMode="numeric" maxLength={11} className="mono" {...register("tcNo")} />
        </Field>
      </FormGrid>
    </AppDialog>
  );
}
