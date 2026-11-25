"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import DailyServiceScreen from "@/features/daily-service/components/DailyServiceScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "PARENT" || role === "STUDENT") return <DailyServiceScreen />;
  return <RoleRedirect />;
}
