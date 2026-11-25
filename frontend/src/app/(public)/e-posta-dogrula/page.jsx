import AuthLayout from "@/features/auth/components/AuthLayout";
import { VerifyEmailStatus } from "@/features/auth/components/PublicAuthForms";

export const metadata = { title: "E-posta doğrulama" };

export default async function Page({ searchParams }) {
  const sp = await searchParams;
  const token = typeof sp?.token === "string" ? sp.token : "";
  return (
    <AuthLayout title="E-posta doğrulama">
      <VerifyEmailStatus token={token} />
    </AuthLayout>
  );
}
