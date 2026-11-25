import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/settings/** — docs/API.md §17 (ADMIN only).
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/settings");
