import { callBackend, passthrough } from "@/lib/auth/proxyHandler";
import { badGateway } from "@/lib/auth/serverApiError";

/**
 * POST /api/auth/register-requests/verify-email — docs/API.md §1. Public, CSRF gerekmez.
 * Body: { "token": "..." }. Token tek kullanımlık/süreli — backend yanıtı olduğu gibi iletilir.
 */
export async function POST(request) {
  const path = "/api/auth/register-requests/verify-email";
  const body = await request.text();

  let backendRes;
  try {
    backendRes = await callBackend(path, { method: "POST", body });
  } catch {
    return badGateway(path);
  }

  return await passthrough(backendRes);
}
