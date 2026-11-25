"use client";

import { useAuth } from "@/providers/AuthProvider";
import RoleRedirect from "@/components/layout/RoleRedirect";
import CorrectionQueueScreen from "@/features/attendance-corrections/components/CorrectionQueueScreen";
import MyCorrectionRequestsScreen from "@/features/attendance-corrections/components/MyCorrectionRequestsScreen";

/** Rol bazlı ekran seçimi (docs/design/NEXT_SPRINT_HANDOFF.md §2). */
export default function Page() {
  const { role } = useAuth();
  if (role === "ADMIN") return <CorrectionQueueScreen />;
  if (role === "DRIVER" || role === "HOSTESS") return <MyCorrectionRequestsScreen />;
  return <RoleRedirect to="/taleplerim?tab=yoklama" />;
}
