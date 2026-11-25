import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/schools/** — docs/API.md §20.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/schools");
