"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import ContractManagementScreen from "@/features/contracts/components/ContractManagementScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <ContractManagementScreen />;
  return <RoleRedirect to="/odemeler" />;
}
