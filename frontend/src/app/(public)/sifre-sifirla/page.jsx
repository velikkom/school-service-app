import AuthLayout from "@/features/auth/components/AuthLayout";
import { ResetPasswordForm } from "@/features/auth/components/PublicAuthForms";

export const metadata = { title: "Şifre sıfırla" };

export default async function Page({ searchParams }) {
  const sp = await searchParams;
  const token = typeof sp?.token === "string" ? sp.token : "";
  return (
    <AuthLayout title="Yeni şifre belirleyin">
      <ResetPasswordForm token={token} />
    </AuthLayout>
  );
}
