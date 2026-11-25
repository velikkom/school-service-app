"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { InputText } from "primereact/inputtext";
import { Button } from "primereact/button";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ScreenState from "@/components/feedback/ScreenState";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { errorMessage } from "@/lib/http/apiError";
import { authService } from "../service/authService";
import { forgotPasswordSchema, registerRequestSchema, resetPasswordSchema } from "../schemas/authSchemas";
import PasswordField from "./PasswordField";
import styles from "../style/auth.module.scss";

const RATE_LIMIT = "Çok fazla deneme yapıldı. Lütfen bir süre sonra tekrar deneyin.";
const msg = (err, fallback) => (err?.status === 429 ? RATE_LIMIT : errorMessage(err, fallback));

/** Kayıt talebi (her zaman 202 — hesap doğrudan açılmaz, yönetici onayı gerekir). */
export function RegisterRequestForm() {
  const [sent, setSent] = useState(false);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(registerRequestSchema), defaultValues: { firstName: "", lastName: "", email: "", password: "", passwordConfirm: "" } });

  const onSubmit = async (values) => {
    setServerError(null);
    try {
      await authService.registerRequest(values);
      setSent(true);
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) setServerError(msg(err, "Talep gönderilemedi."));
    }
  };

  if (sent) {
    return (
      <div className={styles.form}>
        <Notice tone="ok" title="Talebiniz alındı">
          E-posta adresinize bir doğrulama bağlantısı gönderdik. Adresinizi doğruladıktan sonra talebiniz kurum yöneticisinin onayına düşer; onaylandığında giriş yapabilirsiniz.
        </Notice>
        <Link href="/login" className="p-button p-button-secondary" style={{ justifyContent: "center" }}>
          Girişe dön
        </Link>
      </div>
    );
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
      {serverError && <Notice tone="danger">{serverError}</Notice>}
      <FormGrid>
        <Field label="Ad" error={errors.firstName?.message} required>
          <InputText autoComplete="given-name" {...register("firstName")} />
        </Field>
        <Field label="Soyad" error={errors.lastName?.message} required>
          <InputText autoComplete="family-name" {...register("lastName")} />
        </Field>
      </FormGrid>
      <Field label="E-posta" error={errors.email?.message} required>
        <InputText type="email" autoComplete="email" inputMode="email" {...register("email")} />
      </Field>
      <PasswordField control={control} name="password" label="Şifre" error={errors.password?.message} help="En az 8 karakter." />
      <PasswordField control={control} name="passwordConfirm" label="Şifre tekrarı" error={errors.passwordConfirm?.message} />
      <Button type="submit" label="Talep gönder" icon="pi pi-send" loading={isSubmitting} disabled={isSubmitting} />
      <div className={styles.links}>
        <span>
          Hesabınız var mı? <Link href="/login">Giriş yapın</Link>
        </span>
      </div>
    </form>
  );
}

/** Şifremi unuttum (her zaman 202 — hesabın varlığı ifşa edilmez). */
export function ForgotPasswordForm() {
  const [sent, setSent] = useState(false);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(forgotPasswordSchema), defaultValues: { email: "" } });

  const onSubmit = async (values) => {
    setServerError(null);
    try {
      await authService.forgotPassword(values);
      setSent(true);
    } catch (err) {
      setServerError(msg(err, "İstek gönderilemedi."));
    }
  };

  if (sent) {
    return (
      <div className={styles.form}>
        <Notice tone="ok" title="E-postanızı kontrol edin">
          Bu adrese kayıtlı bir hesap varsa şifre sıfırlama bağlantısı gönderildi. Bağlantı sınırlı süre geçerlidir.
        </Notice>
        <Link href="/login" className="p-button p-button-secondary" style={{ justifyContent: "center" }}>
          Girişe dön
        </Link>
      </div>
    );
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
      {serverError && <Notice tone="danger">{serverError}</Notice>}
      <Field label="E-posta" error={errors.email?.message} required>
        <InputText type="email" autoComplete="email" inputMode="email" {...register("email")} />
      </Field>
      <Button type="submit" label="Sıfırlama bağlantısı gönder" icon="pi pi-envelope" loading={isSubmitting} disabled={isSubmitting} />
      <div className={styles.links}>
        <Link href="/login">Girişe dön</Link>
      </div>
    </form>
  );
}

/** Şifre sıfırlama (tek kullanımlık token). */
export function ResetPasswordForm({ token }) {
  const [done, setDone] = useState(false);
  const [serverError, setServerError] = useState(null);
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(resetPasswordSchema), defaultValues: { newPassword: "", confirm: "" } });

  if (!token) {
    return (
      <Notice tone="danger" title="Bağlantı geçersiz">
        Şifre sıfırlama bağlantısı eksik veya hatalı. <Link href="/sifremi-unuttum">Yeni bağlantı isteyin</Link>.
      </Notice>
    );
  }

  const onSubmit = async (values) => {
    setServerError(null);
    try {
      await authService.resetPassword({ token, newPassword: values.newPassword });
      setDone(true);
    } catch (err) {
      setServerError(
        // Sprint 3: backend geçersiz/kullanılmış/süresi dolmuş token için 401 döner (API.md §1); o da bu mesaja düşer.
        [400, 401, 404, 410].includes(err?.status)
          ? "Bağlantının süresi dolmuş veya daha önce kullanılmış. Yeni bir sıfırlama bağlantısı isteyin."
          : msg(err, "Şifre güncellenemedi.")
      );
    }
  };

  if (done) {
    return (
      <div className={styles.form}>
        <Notice tone="ok" title="Şifreniz güncellendi">
          Yeni şifrenizle giriş yapabilirsiniz.
        </Notice>
        <Link href="/login" className="p-button" style={{ justifyContent: "center" }}>
          Giriş yap
        </Link>
      </div>
    );
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
      {serverError && (
        <Notice tone="danger">
          {serverError} <Link href="/sifremi-unuttum">Yeni bağlantı iste</Link>
        </Notice>
      )}
      <PasswordField control={control} name="newPassword" label="Yeni şifre" error={errors.newPassword?.message} help="En az 8 karakter." />
      <PasswordField control={control} name="confirm" label="Yeni şifre tekrarı" error={errors.confirm?.message} />
      <Button type="submit" label="Şifreyi güncelle" icon="pi pi-check" loading={isSubmitting} disabled={isSubmitting} />
    </form>
  );
}

/** E-posta doğrulama: sayfa açılınca token bir kez gönderilir. */
export function VerifyEmailStatus({ token }) {
  const [state, setState] = useState(token ? "loading" : "invalid");
  const sentRef = useRef(false);

  useEffect(() => {
    if (!token || sentRef.current) return;
    sentRef.current = true;
    authService
      .verifyEmail({ token })
      .then(() => setState("ok"))
      .catch(() => setState("invalid"));
  }, [token]);

  if (state === "loading") return <ScreenState kind="loading" rows={2} title="E-posta doğrulanıyor" bare />;
  if (state === "ok") {
    return (
      <div className={styles.form}>
        <Notice tone="ok" title="E-posta adresiniz doğrulandı">
          Kayıt talebiniz kurum yöneticisinin onayına iletildi. Onaylandığında giriş yapabilirsiniz.
        </Notice>
        <Link href="/login" className="p-button p-button-secondary" style={{ justifyContent: "center" }}>
          Girişe dön
        </Link>
      </div>
    );
  }
  return (
    <div className={styles.form}>
      <Notice tone="danger" title="Doğrulama bağlantısı geçersiz">
        Bağlantının süresi dolmuş, daha önce kullanılmış veya hatalı. Gerekirse yeni bir kayıt talebi bırakabilirsiniz.
      </Notice>
      <Link href="/kayit-talebi" className="p-button p-button-secondary" style={{ justifyContent: "center" }}>
        Yeni kayıt talebi
      </Link>
    </div>
  );
}
