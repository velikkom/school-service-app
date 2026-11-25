import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/assignments/** — docs/API.md §8.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/assignments");
