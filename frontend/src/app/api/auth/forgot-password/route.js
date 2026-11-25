import { callBackend, passthrough } from "@/lib/auth/proxyHandler";
import { badGateway } from "@/lib/auth/serverApiError";

/**
 * POST /api/auth/forgot-password — docs/API.md §1. Public, CSRF gerekmez.
 * Body: { "email": "..." }. Backend kullanıcı var/yok fark etmeksizin HER ZAMAN 202 döner
 * (enumeration önleme) — BFF bu davranışı değiştirmez, backend yanıtını olduğu gibi iletir.
 * Ham fetch + response passthrough, lib/proxyHandler.js'teki callBackend/passthrough'a delege edilir.
 */
export async function POST(request) {
  const path = "/api/auth/forgot-password";
  const body = await request.text();

  let backendRes;
  try {
    backendRes = await callBackend(path, { method: "POST", body });
  } catch {
    return badGateway(path);
  }

  return await passthrough(backendRes);
}
