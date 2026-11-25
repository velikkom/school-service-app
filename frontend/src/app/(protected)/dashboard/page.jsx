"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";
import { homePathFor } from "@/lib/rbac/access";
import ScreenState from "@/components/feedback/ScreenState";
import DailyOperationsScreen from "@/features/operations/components/DailyOperationsScreen";

/** ADMIN: Günlük Operasyon. Diğer roller kendi ana ekranına yönlenir. */
export default function DashboardPage() {
  const { role } = useAuth();
  const router = useRouter();
  const isAdmin = role === "ADMIN";

  useEffect(() => {
    if (role && !isAdmin) router.replace(homePathFor(role));
  }, [role, isAdmin, router]);

  if (!isAdmin) return <ScreenState kind="loading" rows={2} />;
  return <DailyOperationsScreen />;
}
