import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/tracking/** — docs/API.md §16.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/tracking");
