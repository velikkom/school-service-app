"use client";

import { useQuery } from "@tanstack/react-query";
import { studentsService } from "@/features/students/service/studentsService";
import { toList } from "@/lib/http/page";
export const myStudentsKey = () => ["students", "mine"];

/**
 * PARENT: bağlı çocuklar; STUDENT: yalnızca kendisi (`content[0]`). Kapsamı backend uygular.
 * Onay bekleyen öz-kayıt öğrencisi de gelir (isActive=true); reddedilen isActive=false.
 */
export function useMyStudents({ enabled = true } = {}) {
  return useQuery({
    queryKey: myStudentsKey(),
    queryFn: async () => toList(await studentsService.list({ size: 100, sort: "fullName,asc" })),
    enabled,
    // Sprint 3: kapsam (veli bağı / öğrenci hesabı bağı) yönetici tarafından değiştirilebilir; ekran her
    // açılışta yeniden doğrulanır ki önbellek kaldırılmış bir erişimi göstermeye devam etmesin.
    staleTime: 0,
  });
}

/** STUDENT'ın kendi öğrenci kaydı (`/me` öğrenci id'si içermez). Kayıt bağlı değilse `null`. */
export function useSelfStudent() {
  const query = useMyStudents();
  return { ...query, student: query.data ? query.data[0] ?? null : undefined };
}
