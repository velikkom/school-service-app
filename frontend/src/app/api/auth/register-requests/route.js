import { callBackend, passthrough } from "@/lib/auth/proxyHandler";
import { badGateway } from "@/lib/auth/serverApiError";

/**
 * POST /api/auth/register-requests — docs/API.md §1, docs/SECURITY.md "Kayıt talebi ve ADMIN
 * onayı". Public, CSRF gerekmez (henüz oturum yok). Backend her durumda 202 döner (var/yok
 * ayrımı yapmaz) — BFF bu davranışı değiştirmez, backend yanıtını olduğu gibi iletir.
 */
export async function POST(request) {
  const path = "/api/auth/register-requests";
  const body = await request.text();

  let backendRes;
  try {
    backendRes = await callBackend(path, { method: "POST", body });
  } catch {
    return badGateway(path);
  }

  return await passthrough(backendRes);
}
