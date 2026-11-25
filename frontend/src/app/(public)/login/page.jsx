import AuthLayout from "@/features/auth/components/AuthLayout";
import LoginForm from "@/features/auth/components/LoginForm";
import { listConfiguredProviders } from "@/lib/auth/oauthProviders";
import { sanitizeLocalPath } from "@/lib/auth/safeRedirect";

export const metadata = { title: "Giriş" };

/** Sunucu bileşeni: `from` hedefini güvenli yerel yola indirger, sağlayıcı bayraklarını (true/false) okur. */
export default async function LoginPage({ searchParams }) {
  const sp = await searchParams;
  return (
    <AuthLayout title="Tekrar hoş geldiniz" lede="Hesabınıza giriş yapın.">
      <LoginForm
        returnTo={sanitizeLocalPath(sp?.from, "/dashboard")}
        expired={sp?.expired === "1"}
        socialError={typeof sp?.socialError === "string" ? sp.socialError : undefined}
        socialPending={sp?.socialPending === "1"}
        socialLink={sp?.socialLink === "1"}
        enabledProviders={listConfiguredProviders()}
      />
    </AuthLayout>
  );
}
