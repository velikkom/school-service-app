import { callBackend, passthrough } from "@/lib/auth/proxyHandler";
import { badGateway } from "@/lib/auth/serverApiError";

/**
 * POST /api/auth/reset-password — docs/API.md §1. Public, CSRF gerekmez (henüz oturum yok).
 * Body: { "token": "...", "newPassword": "..." }
 * Ham fetch + response passthrough, lib/proxyHandler.js'teki callBackend/passthrough'a delege edilir.
 */
export async function POST(request) {
  const path = "/api/auth/reset-password";
  const body = await request.text();

  let backendRes;
  try {
    backendRes = await callBackend(path, { method: "POST", body });
  } catch {
    return badGateway(path);
  }

  return await passthrough(backendRes);
}
