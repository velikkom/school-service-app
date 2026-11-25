import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/registration-requests/** — docs/API.md §18 (sadece ADMIN).
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/registration-requests");
