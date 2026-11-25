import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/users/** — docs/API.md §2 (sadece ADMIN).
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/users");
