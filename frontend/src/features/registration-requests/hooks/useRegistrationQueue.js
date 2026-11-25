"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { registrationRequestsService } from "@/features/registration-requests/service/registrationRequestsService";
import { registrationKeys } from "./keys";

export const REGISTRATION_PAGE_SIZE = 20;

/** status "ALL" → filtre gönderilmez. */
export function useRegistrationQueue({ status, page }) {
  const params = { status: status === "ALL" ? undefined : status, page, size: REGISTRATION_PAGE_SIZE };
  return useQuery({
    queryKey: registrationKeys.list({ status, page }),
    queryFn: () => registrationRequestsService.list(params),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });
}
