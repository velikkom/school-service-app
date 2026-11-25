"use client";

import { Suspense, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
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
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import RecordGate from "@/components/data/RecordGate";
import { TemporaryPasswordNotice } from "@/components/data/AccountPicker";
import { ActiveFilter, SearchFilter, SelectFilter } from "@/components/data/ListFilters";
import { useDialogParams, useListQuery, useListState, useLookup } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation, useRecord } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { optionsOf } from "@/lib/formatting/labels";
import { errorMessage } from "@/lib/http/apiError";
import { toList } from "@/lib/http/page";
import { usersService } from "@/features/users/service/usersService";
import { driversService } from "@/features/drivers/service/driversService";
import { USER_INVALIDATE, userKeys } from "@/features/users/hooks/keys";
import { PROFILE_ROLES, roleChangeSchema, userCreateSchema, userUpdateSchema } from "@/features/users/schemas/userSchema";

const ROLE_OPTIONS = optionsOf("role");

/** /kullanicilar (ADMIN) — hesaplar, rol değiştirme, aktif/pasif. */
export default function UserManagementScreen() {
  return (
    <>
      <PageMeta title="Kullanıcılar" crumb="Yönetim" />
      <Suspense fallback={<ScreenState kind="loading" />}>
        <UserList />
      </Suspense>
    </>
  );
}

/** Aktif/pasif (onaylı). Son aktif ADMIN pasife alınamaz (409 mesajı toast'ta). */
export function useUserActivation() {
  const deactivate = useEntityMutation((id) => usersService.deactivate(id), USER_INVALIDATE);
  const activate = useEntityMutation((id) => usersService.activate(id), USER_INVALIDATE);
  return (u, active) =>
    runConfirmed(
      active
        ? { title: "Hesabı aktifleştir", message: `${u.fullName} yeniden giriş yapabilir.`, acceptLabel: "Aktifleştir", tone: "info", success: "Hesap aktifleştirildi" }
        : {
            title: "Hesabı pasife al",
            message: `${u.fullName} silinmez; tüm oturumları kapanır ve giriş yapamaz. Son aktif yönetici pasife alınamaz.`,
            acceptLabel: "Pasife al",
            success: "Hesap pasife alındı",
            failure: "Hesap pasife alınamadı",
          },
      () => (active ? activate : deactivate).mutateAsync(u.id)
    );
}

function UserList() {
  const list = useListState({ q: "", role: "", isActive: "" });
  const query = useListQuery(userKeys.list(), usersService.list, list.params);
  const dlg = useDialogParams();
  const setActive = useUserActivation();
  const [roleFor, setRoleFor] = useState(null);
  const rows = toList(query.data);
  const fromList = dlg.editId ? rows.find((r) => String(r.id) === dlg.editId) : undefined;

  return (
    <>
      <ManagementList
        ariaLabel="Kullanıcılar"
        query={query}
        page={list.page}
        size={list.size}
        onPage={list.onPage}
        isFiltered={list.isFiltered}
        filters={
          <>
            <SearchFilter label="Ara" placeholder="Ad veya e-posta" value={list.filters.q} onChange={(v) => list.setFilter("q", v)} />
            <SelectFilter label="Rol" value={list.filters.role} onChange={(v) => list.setFilter("role", v)} options={ROLE_OPTIONS} />
            <ActiveFilter value={list.filters.isActive} onChange={(v) => list.setFilter("isActive", v)} />
          </>
        }
        actions={<Button label="Yeni kullanıcı" icon="pi pi-user-plus" onClick={dlg.openNew} />}
        emptyIcon="pi-users"
        emptyMessage="İlk kullanıcıyı “Yeni kullanıcı” ile oluşturun."
        columns={[
          { key: "name", header: "Kullanıcı", main: true, body: (u) => <MainCell title={u.fullName} sub={u.email} href={`/kullanicilar/${u.id}`} /> },
          { key: "role", header: "Rol", body: (u) => <StatusBadge group="role" value={u.role} /> },
          { key: "phone", header: "Telefon", body: (u) => u.phone || "—" },
          { key: "status", header: "Durum", body: (u) => <ActiveBadge value={u.isActive} /> },
        ]}
        rowActions={(u) => (
          <>
            <Button label="Düzenle" icon="pi pi-pencil" size="small" text onClick={() => dlg.openEdit(u.id)} />
            <Button label="Rol değiştir" icon="pi pi-key" size="small" text onClick={() => setRoleFor(u)} />
            {u.isActive ? (
              <Button label="Pasife al" size="small" text severity="danger" onClick={() => setActive(u, false)} />
            ) : (
              <Button label="Aktifleştir" size="small" text onClick={() => setActive(u, true)} />
            )}
          </>
        )}
        footnote="Hesaplar silinmez; pasife alınır. Rol değişince kullanıcının oturumları kapanır."
      />
      <UserFormDialog visible={dlg.isNew || Boolean(dlg.editId)} editId={dlg.editId} fromList={fromList} onHide={dlg.close} />
      {roleFor && <RoleChangeDialog user={roleFor} onHide={() => setRoleFor(null)} />}
    </>
  );
}

/** Kullanıcı yeni/düzenle dialogu (detay ekranı da kullanır). */
export function UserFormDialog({ visible, editId, fromList, onHide }) {
  const { record, isLoading, error } = useRecord(userKeys.detail(), usersService.get, editId, fromList);
  return (
    <RecordGate visible={visible} onHide={onHide} title="Kullanıcıyı düzenle" isLoading={isLoading} error={error}>
      {editId ? <UserEditForm key={editId} record={record} onHide={onHide} /> : <UserCreateForm onHide={onHide} />}
    </RecordGate>
  );
}

function UserCreateForm({ onHide }) {
  const qc = useQueryClient();
  const [serverError, setServerError] = useState(null);
  const [result, setResult] = useState(null); // { email, password, profileError }
  const [submitting, setSubmitting] = useState(false);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(userCreateSchema),
    defaultValues: { fullName: "", email: "", phone: "", role: "PARENT", password: "", licenseNo: "" },
  });
  const role = useWatch({ control, name: "role" });

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    setSubmitting(true);
    try {
      const res = await usersService.create({ fullName: v.fullName, email: v.email, phone: v.phone || undefined, role: v.role, password: v.password || undefined });
      let profileError = null;
      if (PROFILE_ROLES.includes(v.role)) {
        // Profil (şoför/hostes/veli) sunucuda aynı rolle "ensure" edilir (API.md §2).
        try {
          await usersService.changeRole(res.user.id, { role: v.role, licenseNo: v.role === "DRIVER" ? v.licenseNo : undefined });
        } catch (err) {
          profileError = errorMessage(err, "Profil oluşturulamadı.");
        }
      }
      await Promise.all(USER_INVALIDATE.map((queryKey) => qc.invalidateQueries({ queryKey })));
      toastSuccess("Kullanıcı oluşturuldu");
      if (res.temporaryPassword || profileError) setResult({ email: v.email, password: res.temporaryPassword, profileError });
      else onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError, "Kullanıcı oluşturulamadı."));
    } finally {
      setSubmitting(false);
    }
  });

  if (result) {
    return (
      <AppDialog visible onHide={onHide} title="Kullanıcı oluşturuldu" icon="pi-check" tone="ok" cancelLabel="Kapat">
        <TemporaryPasswordNotice email={result.email} password={result.password} />
        {result.profileError && (
          <Notice tone="warn" title="Profil oluşturulamadı">
            {result.profileError} Hesap açıldı; profili tamamlamak için kullanıcıda “Rol değiştir” işlemini aynı rolle tekrarlayın.
          </Notice>
        )}
      </AppDialog>
    );
  }

  return (
    <AppDialog visible onHide={onHide} title="Yeni kullanıcı" icon="pi-user-plus" tone="ok" onSubmit={onSubmit} submitting={submitting} formId="user-form" wide>
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Ad soyad" error={errors.fullName?.message} required>
          <InputText {...register("fullName")} />
        </Field>
        <Field label="E-posta" error={errors.email?.message} required>
          <InputText type="email" inputMode="email" autoComplete="off" {...register("email")} />
        </Field>
        <Field label="Telefon" error={errors.phone?.message}>
          <InputText type="tel" inputMode="tel" {...register("phone")} />
        </Field>
        <Field label="Rol" error={errors.role?.message} required htmlFor="user-role">
          <Controller
            name="role"
            control={control}
            render={({ field }) => <Dropdown inputId="user-role" value={field.value} options={ROLE_OPTIONS} onChange={(e) => field.onChange(e.value)} />}
          />
        </Field>
        {role === "DRIVER" && (
          <Field label="Ehliyet no" error={errors.licenseNo?.message} required>
            <InputText className="mono" {...register("licenseNo")} />
          </Field>
        )}
        <Field label="Şifre" error={errors.password?.message} help="Boş bırakılırsa geçici şifre üretilir ve bir kez gösterilir.">
          <InputText type="password" autoComplete="new-password" {...register("password")} />
        </Field>
      </FormGrid>
      {PROFILE_ROLES.includes(role) && <p className="text-xs muted">Şoför, hostes ve veli hesaplarında ilgili profil aynı işlemde oluşturulur.</p>}
    </AppDialog>
  );
}

function UserEditForm({ record, onHide }) {
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(userUpdateSchema), defaultValues: { fullName: record?.fullName ?? "", phone: record?.phone ?? "" } });
  const save = useEntityMutation((body) => usersService.update(record.id, body), USER_INVALIDATE);
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await save.mutateAsync({ fullName: v.fullName, phone: v.phone || undefined });
      toastSuccess("Kullanıcı güncellendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError));
    }
  });
  return (
    <AppDialog visible onHide={onHide} title="Kullanıcıyı düzenle" subtitle={record?.email} icon="pi-pencil" tone="info" onSubmit={onSubmit} submitting={save.isPending} formId="user-edit-form">
      {serverError && <Notice tone="danger" title="Kaydedilemedi">{serverError}</Notice>}
      <FormGrid>
        <Field label="Ad soyad" error={errors.fullName?.message} required>
          <InputText {...register("fullName")} />
        </Field>
        <Field label="Telefon" error={errors.phone?.message}>
          <InputText type="tel" inputMode="tel" {...register("phone")} />
        </Field>
      </FormGrid>
      <p className="text-xs muted">E-posta değiştirilemez. Rol ve aktiflik ayrı işlemlerdir.</p>
    </AppDialog>
  );
}

/** Rol değiştir dialogu → POST /users/{id}/role. DRIVER hedefinde şoför profili yoksa ehliyet no zorunlu. */
export function RoleChangeDialog({ user, onHide }) {
  const drivers = useLookup(["drivers"], () => driversService.list({ size: 100 }));
  const hasDriverProfile = drivers.items.some((d) => String(d.userId) === String(user.id));
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(roleChangeSchema({ hasDriverProfile: true })), defaultValues: { role: user.role, licenseNo: "" } });
  const role = useWatch({ control, name: "role" });
  const save = useEntityMutation((body) => usersService.changeRole(user.id, body), USER_INVALIDATE);
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    // Şoför profili bilgisi sonradan yüklenebildiği için bu kural gönderimde uygulanır.
    const check = roleChangeSchema({ hasDriverProfile }).safeParse(v);
    if (!check.success) {
      check.error.issues.forEach((i) => setError(i.path[0], { type: "manual", message: i.message }));
      return;
    }
    try {
      await save.mutateAsync({ role: v.role, licenseNo: v.role === "DRIVER" && v.licenseNo ? v.licenseNo : undefined });
      toastSuccess("Rol güncellendi");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError, "Rol değiştirilemedi."));
    }
  });
  return (
    <AppDialog visible onHide={onHide} title="Rol değiştir" subtitle={user.fullName} icon="pi-key" tone="warn" submitLabel="Rolü değiştir" onSubmit={onSubmit} submitting={save.isPending} formId="role-form">
      {serverError && <Notice tone="danger" title="Rol değiştirilemedi">{serverError}</Notice>}
      <Field label="Yeni rol" error={errors.role?.message} required htmlFor="rc-role">
        <Controller name="role" control={control} render={({ field }) => <Dropdown inputId="rc-role" value={field.value} options={ROLE_OPTIONS} onChange={(e) => field.onChange(e.value)} />} />
      </Field>
      {role === "DRIVER" && (
        <Field
          label="Ehliyet no"
          error={errors.licenseNo?.message}
          required={!hasDriverProfile}
          help={hasDriverProfile ? "Mevcut şoför profili yeniden kullanılır; boş bırakılabilir." : "Şoför profili yok; ehliyet no zorunlu."}
        >
          <InputText className="mono" {...register("licenseNo")} />
        </Field>
      )}
      <Notice tone="warn">Rol değişince kullanıcının tüm oturumları kapanır. Gerekli profil (veli/şoför/hostes) aynı işlemde oluşturulur. Son aktif yöneticinin rolü düşürülemez.</Notice>
    </AppDialog>
  );
}
