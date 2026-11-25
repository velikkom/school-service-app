import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/notifications/** — docs/API.md §15.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/notifications");
