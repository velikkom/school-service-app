import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/trips/** (sefer + biniş/iniş/devamsızlık) — docs/API.md §9, §10.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/trips");
