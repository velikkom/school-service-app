import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/reports/** — docs/API.md §14 (ADMIN only, backend doğrular).
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/reports");
