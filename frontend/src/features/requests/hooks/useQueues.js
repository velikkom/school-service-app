"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { stopChangeRequestsService } from "@/features/stop-change-requests/service/stopChangeRequestsService";
import { contractsService } from "@/features/contracts/service/contractsService";
import { settingsService } from "@/features/settings/service/settingsService";
import { studentsService } from "@/features/students/service/studentsService";
import { parentsService } from "@/features/parents/service/parentsService";
import { requestKeys } from "./keys";

export const QUEUE_PAGE_SIZE = 20;

export function useStopChangeQueue({ status, page }) {
  const params = { status, page, size: QUEUE_PAGE_SIZE };
  return useQuery({
    queryKey: requestKeys.stopChanges(params),
    queryFn: () => stopChangeRequestsService.list(params),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });
}

export function useEnrollmentQueue({ page }) {
  const params = { page, size: QUEUE_PAGE_SIZE };
  return useQuery({
    queryKey: requestKeys.enrollments(params),
    queryFn: () => contractsService.list({ status: "PENDING_APPROVAL", ...params }),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });
}

/** Kurum ayarları — onay dialogunda varsayılan aylık ücret önerisi için. */
export function useInstitutionSettings() {
  return useQuery({ queryKey: requestKeys.settings(), queryFn: settingsService.get, staleTime: 5 * 60_000 });
}

export function useApplicantDetails(studentId) {
  const student = useQuery({
    queryKey: requestKeys.student(studentId),
    queryFn: () => studentsService.get(studentId),
    enabled: Boolean(studentId),
  });
  const links = useQuery({
    queryKey: requestKeys.studentLinks(studentId),
    queryFn: () => parentsService.links({ studentId }),
    enabled: Boolean(studentId),
  });
  const linkList = Array.isArray(links.data) ? links.data : (links.data?.content ?? []);
  const parentId = (linkList.find((l) => l.isActive) ?? linkList[0])?.parentId;
  const parent = useQuery({
    queryKey: requestKeys.parent(parentId),
    queryFn: () => parentsService.get(parentId),
    enabled: Boolean(parentId),
  });
  return { student, parent: parentId ? parent : null };
}
