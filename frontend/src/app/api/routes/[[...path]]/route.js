import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/routes/** (route+stop) — docs/API.md §7.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/routes");
