"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { tripsService } from "@/features/trips/service/tripsService";
import { routesService } from "@/features/routes/service/routesService";
import { vehiclesService } from "@/features/vehicles/service/vehiclesService";
import { driversService } from "@/features/drivers/service/driversService";
import { hostessesService } from "@/features/hostesses/service/hostessesService";
import { usersService } from "@/features/users/service/usersService";
import { toList } from "@/lib/http/page";
import { staffOptions, sortTrips } from "@/features/trips/lib/fieldUtils";
import { tripKeys } from "./keys";

const LIVE_MS = 15_000;

/** ADMIN sayfalı sefer listesi. */
export function useTripList(params) {
  return useQuery({
    queryKey: tripKeys.list(params),
    queryFn: () => tripsService.list(params),
    placeholderData: keepPreviousData,
  });
}

/** DRIVER/HOSTESS: tarihli `trips/mine` (tarih her zaman gönderilir), istemcide sıralanmış. */
export function useMyTrips(date, { enabled = true } = {}) {
  return useQuery({
    queryKey: tripKeys.mine(date),
    enabled: Boolean(date) && enabled,
    queryFn: async () => sortTrips(toList(await tripsService.mine(date))),
    refetchInterval: LIVE_MS,
  });
}

export function useTrip(id, { live = true } = {}) {
  return useQuery({
    queryKey: tripKeys.detail(id),
    enabled: Boolean(id),
    queryFn: () => tripsService.get(id),
    refetchInterval: live ? LIVE_MS : false,
    retry: (count, err) => err?.status !== 404 && err?.status !== 403 && count < 2,
  });
}

export function useRoster(id, { enabled = true, live = true } = {}) {
  return useQuery({
    queryKey: tripKeys.roster(id),
    enabled: Boolean(id) && enabled,
    queryFn: async () => toList(await tripsService.roster(id)),
    refetchInterval: live ? LIVE_MS : false,
    placeholderData: keepPreviousData,
  });
}

export function useIncidents(id, { enabled = true, live = true } = {}) {
  return useQuery({
    queryKey: tripKeys.incidents(id),
    enabled: Boolean(id) && enabled,
    queryFn: async () => toList(await tripsService.incidents(id)),
    refetchInterval: live ? LIVE_MS : false,
  });
}

export function useAbsenceNotices(id, { enabled = true } = {}) {
  return useQuery({
    queryKey: tripKeys.notices(id),
    enabled: Boolean(id) && enabled,
    queryFn: async () => toList(await tripsService.absenceNotices(id)),
    refetchInterval: LIVE_MS,
  });
}

export function useRouteStops(routeId, { enabled = true } = {}) {
  return useQuery({
    queryKey: tripKeys.routeStops(routeId),
    enabled: Boolean(routeId) && enabled,
    queryFn: async () => [...toList(await routesService.stops(routeId))].sort((a, b) => a.sequenceNo - b.sequenceNo),
    staleTime: 5 * 60_000,
  });
}

/**
 * ADMIN sefer formları için seçim listeleri. Şoför/hostes profilinde ad yok (API ref §10 "İsim
 * eksikleri"): ad, `GET /api/users?role=` ile `userId` eşlenerek bulunur.
 */
export function useTripLookups({ enabled = true } = {}) {
  return useQuery({
    queryKey: tripKeys.lookups("form"),
    enabled,
    staleTime: 5 * 60_000,
    queryFn: async () => {
      const [routes, vehicles, drivers, hostesses, driverUsers, hostessUsers] = await Promise.all([
        routesService.list({ isActive: true, size: 100, sort: "name,asc" }),
        vehiclesService.list({ isActive: true, size: 100, sort: "plate,asc" }),
        driversService.list({ isActive: true, size: 100 }),
        hostessesService.list({ isActive: true, size: 100 }),
        usersService.list({ role: "DRIVER", size: 100 }),
        usersService.list({ role: "HOSTESS", size: 100 }),
      ]);
      return {
        routes: toList(routes),
        vehicles: toList(vehicles),
        drivers: staffOptions(toList(drivers), toList(driverUsers)),
        hostesses: staffOptions(toList(hostesses), toList(hostessUsers)),
      };
    },
  });
}
