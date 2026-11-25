import AuthLayout from "@/features/auth/components/AuthLayout";
import { RegisterRequestForm } from "@/features/auth/components/PublicAuthForms";

export const metadata = { title: "Kayıt talebi" };

export default function Page() {
  return (
    <AuthLayout title="Kayıt talebi" lede="Talebiniz e-posta doğrulamasından sonra kurum yöneticisinin onayına gider.">
      <RegisterRequestForm />
    </AuthLayout>
  );
}
