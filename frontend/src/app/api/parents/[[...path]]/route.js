import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/parents/** — docs/API.md §4.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/parents");
