"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { InputText } from "primereact/inputtext";
import { Password } from "primereact/password";
import { Checkbox } from "primereact/checkbox";
import { Button } from "primereact/button";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { loginSchema } from "../schemas/authSchemas";
import { authService } from "../service/authService";
import { authKeys } from "../hooks/keys";
import styles from "../style/auth.module.scss";

const LOGIN_ERRORS = {
  401: "E-posta veya şifre hatalı.",
  403: "Hesabınız pasif durumda. Lütfen kurum yöneticinizle iletişime geçin.",
  429: "Çok fazla deneme yapıldı. Lütfen bir süre sonra tekrar deneyin.",
};

const SOCIAL_ERRORS = {
  unconfigured: "Bu sosyal giriş seçeneği şu an kullanılamıyor.",
  state_mismatch: "Sosyal giriş isteği doğrulanamadı, lütfen tekrar deneyin.",
  invalid_response: "Sosyal giriş sağlayıcısından geçerli bir yanıt alınamadı.",
  token_exchange_failed: "Sosyal giriş tamamlanamadı, lütfen tekrar deneyin.",
  userinfo_failed: "Sosyal hesap bilgileri alınamadı, lütfen tekrar deneyin.",
  backend_unreachable: "Sunucuya şu anda erişilemiyor. Lütfen daha sonra tekrar deneyin.",
  backend_error: "Sosyal giriş tamamlanamadı, lütfen tekrar deneyin.",
};

const PROVIDER_LABELS = { GOOGLE: "Google", FACEBOOK: "Facebook", X: "X" };

export default function LoginForm({ returnTo, expired, socialError, socialPending, socialLink, enabledProviders = {} }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [serverError, setServerError] = useState(socialError ? SOCIAL_ERRORS[socialError] || SOCIAL_ERRORS.backend_error : null);
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "", rememberMe: false } });

  const onSubmit = async (values) => {
    setServerError(null);
    try {
      const { user } = await authService.login(values);
      if (socialLink) {
        await authService.socialLink().catch(() => {});
      }
      // Önceki hesabın önbelleği kalmasın.
      queryClient.clear();
      queryClient.setQueryData(authKeys.me(), user);
      router.replace(returnTo || "/dashboard");
      router.refresh();
    } catch (err) {
      setServerError(LOGIN_ERRORS[err?.status] || err?.message || "Giriş yapılamadı. Lütfen tekrar deneyin.");
    }
  };

  const providers = Object.entries(enabledProviders).filter(([, on]) => on);

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
      {expired && !serverError && (
        <Notice tone="info" title="Oturumunuz sona erdi">
          Devam etmek için yeniden giriş yapın.
        </Notice>
      )}
      {socialPending && !serverError && (
        <Notice tone="info" title="Kayıt talebiniz alındı">
          Yönetici onayından sonra giriş yapabilirsiniz.
        </Notice>
      )}
      {socialLink && !serverError && (
        <Notice tone="info" title="Hesap bağlama">
          Sosyal hesabınızı bağlamak için mevcut şifrenizle giriş yapın.
        </Notice>
      )}
      {serverError && (
        <Notice tone="danger" title="Giriş yapılamadı">
          {serverError}
        </Notice>
      )}

      <Field label="E-posta" error={errors.email?.message} required>
        <InputText type="email" autoComplete="email" inputMode="email" {...register("email")} />
      </Field>

      <Field label="Şifre" error={errors.password?.message} required htmlFor="login-password">
        <Controller
          name="password"
          control={control}
          render={({ field }) => (
            <Password
              inputId="login-password"
              value={field.value}
              onChange={(e) => field.onChange(e.target.value)}
              onBlur={field.onBlur}
              feedback={false}
              toggleMask
              autoComplete="current-password"
              invalid={Boolean(errors.password)}
              inputClassName="w-full"
              pt={{ input: { "aria-invalid": errors.password ? true : undefined }, showIcon: { "aria-label": "Şifreyi göster" }, hideIcon: { "aria-label": "Şifreyi gizle" } }}
            />
          )}
        />
      </Field>

      <div className={styles.rowBetween}>
        <Controller
          name="rememberMe"
          control={control}
          render={({ field }) => (
            <label className={styles.check} htmlFor="remember-me">
              <Checkbox inputId="remember-me" checked={Boolean(field.value)} onChange={(e) => field.onChange(e.checked)} />
              Beni hatırla
            </label>
          )}
        />
        <Link href="/sifremi-unuttum">Şifremi unuttum</Link>
      </div>

      <Button type="submit" label="Giriş yap" icon="pi pi-sign-in" loading={isSubmitting} disabled={isSubmitting} />

      {providers.length > 0 && (
        <>
          <div className={styles.divider}>veya</div>
          {providers.map(([id]) => (
            <a key={id} className="p-button p-button-secondary" href={`/api/auth/social/${id}/start`} style={{ justifyContent: "center" }}>
              {PROVIDER_LABELS[id] ?? id} ile devam et
            </a>
          ))}
        </>
      )}

      <div className={styles.links}>
        <span>
          Hesabınız yok mu? <Link href="/kayit-talebi">Kayıt talebi bırakın</Link>
        </span>
      </div>
    </form>
  );
}
