import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/billing/** — docs/API.md §12.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/billing");
