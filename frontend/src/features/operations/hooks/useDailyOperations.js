"use client";

import { keepPreviousData, useQueries, useQuery } from "@tanstack/react-query";
import { reportsService } from "@/features/reports/service/reportsService";
import { tripsService } from "@/features/trips/service/tripsService";
import { toList } from "@/lib/http/page";
import { operationKeys } from "./keys";

/** Kart başına yoklama/olay isteği atılan azami sefer sayısı (toplu uç yok — sefer başına istek). */
export const PER_TRIP_FETCH_LIMIT = 30;

const DIRECTION_ORDER = { MORNING: 0, AFTERNOON: 1 };

/** Seferleri planlanan saat, sonra yön sırasıyla dizer (API bu sıralamayı desteklemez). */
export function sortTrips(trips) {
  return [...trips].sort((a, b) => {
    const ta = a.plannedStartTime || "99:99";
    const tb = b.plannedStartTime || "99:99";
    if (ta !== tb) return ta.localeCompare(tb);
    const d = (DIRECTION_ORDER[a.direction] ?? 9) - (DIRECTION_ORDER[b.direction] ?? 9);
    return d !== 0 ? d : a.id - b.id;
  });
}

/** Yolcu listesinden yoklama sayacı: toplam / işlem görmüş / gelmedi / veli bildirimi. */
export function rosterCounts(roster) {
  const list = Array.isArray(roster) ? roster : [];
  return {
    total: list.length,
    recorded: list.filter((r) => r.status && r.status !== "NONE").length,
    absent: list.filter((r) => r.status === "ABSENT").length,
    notices: list.filter((r) => r.absenceNotice).length,
  };
}

export function useDailySummary(date, { live }) {
  return useQuery({
    queryKey: operationKeys.summary(date),
    queryFn: () => reportsService.dailySummary(date),
    placeholderData: keepPreviousData,
    refetchInterval: live ? 60_000 : false,
  });
}

export function useTripsOfDay(date, { live }) {
  return useQuery({
    queryKey: operationKeys.trips(date),
    queryFn: () => tripsService.list({ businessDate: date, size: 100 }),
    select: (resp) => ({ trips: sortTrips(toList(resp)), totalElements: resp?.totalElements ?? toList(resp).length }),
    placeholderData: keepPreviousData,
    refetchInterval: live ? 30_000 : false,
  });
}

/**
 * Sefer başına yolcu listesi ve operasyon kayıtları. Sefer sayısı makul sınırı aşarsa istek atılmaz
 * (`enabled=false`) ve ekran sayaçları göstermez.
 */
export function usePerTripDetails(trips, { live }) {
  const enabled = trips.length > 0 && trips.length <= PER_TRIP_FETCH_LIMIT;
  const interval = live ? 60_000 : false;

  const rosters = useQueries({
    queries: (enabled ? trips : []).map((t) => ({
      queryKey: operationKeys.roster(t.id),
      queryFn: () => tripsService.roster(t.id),
      refetchInterval: interval,
    })),
  });

  const incidents = useQueries({
    queries: (enabled ? trips : []).map((t) => ({
      queryKey: operationKeys.incidents(t.id),
      queryFn: () => tripsService.incidents(t.id),
      refetchInterval: interval,
    })),
  });

  const rosterByTrip = {};
  const incidentsByTrip = {};
  if (enabled) {
    trips.forEach((t, i) => {
      rosterByTrip[t.id] = rosters[i]?.data;
      incidentsByTrip[t.id] = incidents[i]?.data;
    });
  }

  return {
    enabled,
    rosterByTrip,
    incidentsByTrip,
    incidentsLoading: incidents.some((q) => q.isPending),
    incidentsError: incidents.some((q) => q.isError && !q.data),
  };
}
