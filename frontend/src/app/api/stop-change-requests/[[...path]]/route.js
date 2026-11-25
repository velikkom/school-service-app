import { createDomainProxy } from "@/lib/auth/proxyHandler";

// Allowlist: sadece backend /api/stop-change-requests/** — veli durak değişikliği talepleri.
export const { GET, POST, PATCH, PUT, DELETE } = createDomainProxy("/api/stop-change-requests");
