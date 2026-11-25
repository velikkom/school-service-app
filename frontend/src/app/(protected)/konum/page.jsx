"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import FieldActiveTripRedirect from "@/features/trips/components/FieldActiveTripRedirect";
import LiveLocationScreen from "@/features/tracking/components/LiveLocationScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <LiveLocationScreen />;
  if (role === "DRIVER" || role === "HOSTESS") return <FieldActiveTripRedirect tab="konum" />;
  return <RoleRedirect to="/servisim" />;
}
