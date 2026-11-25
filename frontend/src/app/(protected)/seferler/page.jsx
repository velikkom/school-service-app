"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import AdminTripListScreen from "@/features/trips/components/AdminTripListScreen";
import FieldTodayScreen from "@/features/trips/components/FieldTodayScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <AdminTripListScreen />;
  if (role === "DRIVER" || role === "HOSTESS") return <FieldTodayScreen />;
  return <RoleRedirect />;
}
