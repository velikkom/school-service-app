import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/drivers/** — docs/API.md §6.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/drivers");
