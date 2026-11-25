"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import FieldActiveTripRedirect from "@/features/trips/components/FieldActiveTripRedirect";
import AttendanceHistoryScreen from "@/features/attendance/components/AttendanceHistoryScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "DRIVER" || role === "HOSTESS") return <FieldActiveTripRedirect tab="yoklama" />;
  if (role === "PARENT" || role === "STUDENT") return <AttendanceHistoryScreen />;
  return <RoleRedirect />;
}
