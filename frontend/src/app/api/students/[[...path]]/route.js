import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/students/** — docs/API.md §3.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/students");
