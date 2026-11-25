"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import RouteManagementScreen from "@/features/routes/components/RouteManagementScreen";
import MyRouteScreen from "@/features/trips/components/MyRouteScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <RouteManagementScreen />;
  if (role === "DRIVER" || role === "HOSTESS") return <MyRouteScreen />;
  return <RoleRedirect />;
}
