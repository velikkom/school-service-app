"use client";

import { Suspense } from "react";
import { useAuth } from "@/providers/AuthProvider";
import ScreenState from "@/components/feedback/ScreenState";
import AdminTripDetail from "./AdminTripDetail";
import FieldTripDetail from "./FieldTripDetail";

/**
 * /seferler/[id] — rol bazlı sefer ekranı. ADMIN: yönetim görünümü (doğrudan gerekçeli iptal dahil).
 * DRIVER/HOSTESS: saha görünümü (yoklama, konum, olay, yalnız İPTAL TALEBİ — doğrudan iptal yok).
 */
export default function TripDetailScreen({ id }) {
  const { role } = useAuth();
  let content;
  if (role === "ADMIN") content = <AdminTripDetail id={id} />;
  else if (role === "DRIVER" || role === "HOSTESS") content = <FieldTripDetail id={id} role={role} />;
  else content = <ScreenState kind="forbidden" homeHref="/" />;
  return <Suspense fallback={<ScreenState kind="loading" />}>{content}</Suspense>;
}
