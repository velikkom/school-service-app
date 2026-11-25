"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import VehicleManagementScreen from "@/features/vehicles/components/VehicleManagementScreen";
import MyVehicleScreen from "@/features/trips/components/MyVehicleScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <VehicleManagementScreen />;
  if (role === "DRIVER") return <MyVehicleScreen />;
  return <RoleRedirect />;
}
