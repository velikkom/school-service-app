import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/vehicles/** — docs/API.md §5.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/vehicles");
