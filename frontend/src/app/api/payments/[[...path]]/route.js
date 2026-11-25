import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/payments/** — docs/API.md §13. (Idempotency-Key header geçirilir.)
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/payments");
