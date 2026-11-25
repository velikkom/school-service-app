"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import AssignmentManagementScreen from "@/features/assignments/components/AssignmentManagementScreen";
import StudentRouteScreen from "@/features/family/components/StudentRouteScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <AssignmentManagementScreen />;
  if (role === "STUDENT") return <StudentRouteScreen />;
  return <RoleRedirect />;
}
