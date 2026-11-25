"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import PaymentsScreen from "@/features/payments/components/PaymentsScreen";
import FamilyFinanceScreen from "@/features/family/components/FamilyFinanceScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <PaymentsScreen />;
  if (role === "PARENT") return <FamilyFinanceScreen />;
  return <RoleRedirect />;
}
