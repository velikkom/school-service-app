"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import StudentManagementScreen from "@/features/students/components/StudentManagementScreen";
import MyChildrenScreen from "@/features/family/components/MyChildrenScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <StudentManagementScreen />;
  if (role === "PARENT") return <MyChildrenScreen />;
  return <RoleRedirect to="/profil" />;
}
