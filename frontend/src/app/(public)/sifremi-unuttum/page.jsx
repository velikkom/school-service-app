import AuthLayout from "@/features/auth/components/AuthLayout";
import { ForgotPasswordForm } from "@/features/auth/components/PublicAuthForms";

export const metadata = { title: "Şifremi unuttum" };

export default function Page() {
  return (
    <AuthLayout title="Şifremi unuttum" lede="Hesabınıza kayıtlı e-posta adresini girin.">
      <ForgotPasswordForm />
    </AuthLayout>
  );
}
