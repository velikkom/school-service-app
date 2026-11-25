import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/attendance-corrections/** — yoklama düzeltme/inceleme talepleri.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/attendance-corrections");
