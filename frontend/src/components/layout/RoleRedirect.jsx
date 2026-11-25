"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";
import { homePathFor } from "@/lib/rbac/access";
import ScreenState from "@/components/feedback/ScreenState";

/** Rol için bu route'un karşılığı başka bir ekransa oraya (varsayılan: rolün ana ekranı) yönlendirir. */
export default function RoleRedirect({ to }) {
  const { role } = useAuth();
  const router = useRouter();
  const target = to || homePathFor(role);
  useEffect(() => {
    if (role) router.replace(target);
  }, [role, target, router]);
  return <ScreenState kind="loading" rows={2} title="Yönlendiriliyor" />;
}
