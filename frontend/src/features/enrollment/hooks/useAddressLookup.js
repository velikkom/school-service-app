"use client";

import { useQuery } from "@tanstack/react-query";
import { addressLookupService } from "@/features/enrollment/service/addressLookupService";
import { enrollmentKeys } from "./keys";

const DAY = 24 * 60 * 60 * 1000;
// Üçüncü taraf servis: hata olursa yeniden denemeden serbest metne düşülür.
const common = { staleTime: DAY, gcTime: DAY, retry: false, refetchOnWindowFocus: false };

export function useProvinces() {
  return useQuery({ queryKey: enrollmentKeys.provinces(), queryFn: addressLookupService.provinces, ...common });
}

export function useDistricts(provinceId) {
  return useQuery({
    queryKey: enrollmentKeys.districts(provinceId),
    queryFn: () => addressLookupService.districts(provinceId),
    enabled: Boolean(provinceId),
    ...common,
  });
}

export function useNeighborhoods(districtId) {
  return useQuery({
    queryKey: enrollmentKeys.neighborhoods(districtId),
    queryFn: () => addressLookupService.neighborhoods(districtId),
    enabled: Boolean(districtId),
    ...common,
  });
}
