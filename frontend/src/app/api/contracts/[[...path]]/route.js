import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/contracts/** — docs/API.md §11.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/contracts");
