"use client";

import { useQuery } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "primereact/button";
import PageMeta from "@/components/layout/PageMeta";
import Card, { DefinitionList } from "@/components/ui/Card";
import Notice, { Avatar } from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import ScreenState from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import { useAuth } from "@/providers/AuthProvider";
import { ROLE_LABELS, GENDER_LABELS } from "@/lib/formatting/labels";
import { formatDate } from "@/lib/formatting/date";
import { errorMessage } from "@/lib/http/apiError";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { toList } from "@/lib/http/page";
import { driversService } from "@/features/drivers/service/driversService";
import { hostessesService } from "@/features/hostesses/service/hostessesService";
import { parentsService } from "@/features/parents/service/parentsService";
import { studentsService } from "@/features/students/service/studentsService";
import { authService } from "../service/authService";
import { changePasswordSchema } from "../schemas/authSchemas";
import PasswordField from "./PasswordField";
import styles from "../style/profile.module.scss";

const profileLoaders = {
  DRIVER: () => driversService.me(),
  HOSTESS: () => hostessesService.me(),
  PARENT: () => parentsService.me(),
  STUDENT: async () => toList(await studentsService.list({ size: 1 }))[0] ?? null,
};

function RoleProfile({ role, data }) {
  if (!data) return <p className="muted">Bu hesaba bağlı bir {ROLE_LABELS[role]?.toLocaleLowerCase("tr")} kaydı bulunamadı.</p>;
  if (role === "DRIVER") return <DefinitionList items={[["Ehliyet no", data.licenseNo], ["Durum", <StatusBadge key="s" group="active" value={String(data.isActive)} />]]} />;
  if (role === "HOSTESS") return <DefinitionList items={[["Durum", <StatusBadge key="s" group="active" value={String(data.isActive)} />]]} />;
  if (role === "PARENT") return <DefinitionList items={[["Ad soyad", data.fullName], ["Telefon", data.phone || "—"]]} />;
  return (
    <DefinitionList
      items={[
        ["Ad soyad", data.fullName],
        ["Doğum tarihi", formatDate(data.birthDate)],
        ["Okul", data.schoolName || "—"],
        ["Sınıf", data.grade || "—"],
        ["Cinsiyet", GENDER_LABELS[data.gender] ?? "—"],
      ]}
    />
  );
}

function ChangePasswordCard() {
  const { logout } = useAuth();
  const {
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(changePasswordSchema), defaultValues: { currentPassword: "", newPassword: "", confirm: "" } });

  const onSubmit = async (values) => {
    try {
      await authService.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword });
      reset();
      toastSuccess("Şifreniz değiştirildi", "Güvenlik için tüm oturumlar kapatıldı; yeni şifrenizle giriş yapın.");
      setTimeout(() => logout(), 1500);
    } catch (err) {
      if (applyServerFieldErrors(err, setError)) return;
      setError(err?.status === 400 || err?.status === 401 ? "currentPassword" : "root", {
        message: err?.status === 400 || err?.status === 401 ? "Mevcut şifre hatalı." : errorMessage(err, "Şifre değiştirilemedi."),
      });
    }
  };

  return (
    <Card title="Şifre değiştir" subtitle="Başarılı değişiklikte bu dahil tüm oturumlarınız kapanır.">
      <form className="stack-lg" onSubmit={handleSubmit(onSubmit)} noValidate>
        {errors.root && <Notice tone="danger">{errors.root.message}</Notice>}
        <PasswordField control={control} name="currentPassword" label="Mevcut şifre" autoComplete="current-password" error={errors.currentPassword?.message} />
        <PasswordField control={control} name="newPassword" label="Yeni şifre" error={errors.newPassword?.message} help="En az 8 karakter." />
        <PasswordField control={control} name="confirm" label="Yeni şifre tekrarı" error={errors.confirm?.message} />
        <div>
          <Button type="submit" label="Şifreyi değiştir" icon="pi pi-lock" loading={isSubmitting} disabled={isSubmitting} />
        </div>
      </form>
    </Card>
  );
}

/** Profil / Bilgilerim (tüm roller): hesap bilgisi, rol profili (salt okunur), şifre değiştirme, çıkış. */
export default function ProfileScreen() {
  const { user, logout } = useAuth();
  const role = user.role;
  const profile = useQuery({
    queryKey: ["profile", role, user.id],
    queryFn: async () => {
      try {
        return (await profileLoaders[role]?.()) ?? null;
      } catch (err) {
        if (err?.status === 404) return null;
        throw err;
      }
    },
    enabled: Boolean(profileLoaders[role]),
  });

  return (
    <>
      <PageMeta title={role === "STUDENT" ? "Bilgilerim" : "Profil"} />
      <div className="grid-main">
        <div className="stack-lg">
          <Card>
            <div className={styles.head}>
              <Avatar name={user.fullName} size={56} />
              <div>
                <div className={styles.name}>{user.fullName}</div>
                <div className="muted">{user.email}</div>
                <StatusBadge group="role" value={role} />
              </div>
            </div>
          </Card>
          {profileLoaders[role] && (
            <Card title={role === "STUDENT" ? "Öğrenci bilgileri" : `${ROLE_LABELS[role]} bilgileri`} subtitle="Değişiklik için kurum yöneticisiyle iletişime geçin.">
              {profile.isPending ? (
                <ScreenState kind="loading" rows={2} bare />
              ) : profile.isError ? (
                <ScreenState kind="error" error={profile.error} onRetry={profile.refetch} bare compact />
              ) : (
                <RoleProfile role={role} data={profile.data} />
              )}
            </Card>
          )}
        </div>
        <div className="stack-lg">
          <ChangePasswordCard />
          <Card title="Oturum">
            <Button type="button" label="Çıkış yap" icon="pi pi-sign-out" className="p-button-secondary" onClick={logout} />
          </Card>
        </div>
      </div>
    </>
  );
}
