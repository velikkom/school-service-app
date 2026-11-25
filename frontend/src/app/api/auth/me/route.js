import { readAuthCookies, setAuthCookies, clearAuthCookies } from "@/lib/auth/authCookies";
import { refreshAccessToken } from "@/lib/auth/authSession";
import { callBackend, passthrough } from "@/lib/auth/proxyHandler";
import { unauthorized, badGateway } from "@/lib/auth/serverApiError";

/**
 * GET /api/auth/me — oturumdaki kullanıcı. ssp_rt bu yolda gönderildiği için access token süresi
 * dolmuş/çerezi silinmişse burada yenilenir. Yenileme başarısızsa çerezler temizlenir.
 */
export async function GET(request) {
  const path = "/api/auth/me";
  const { accessToken, refreshToken } = readAuthCookies(request);

  if (accessToken) {
    let res;
    try {
      res = await callBackend(path, { method: "GET", accessToken });
    } catch {
      return badGateway(path);
    }
    if (res.status !== 401) return await passthrough(res);
  }

  if (!refreshToken) return clearAuthCookies(unauthorized(path));

  const refreshed = await refreshAccessToken(refreshToken).catch(() => null);
  if (!refreshed) return clearAuthCookies(unauthorized(path));

  let retry;
  try {
    retry = await callBackend(path, { method: "GET", accessToken: refreshed.accessToken });
  } catch {
    return badGateway(path);
  }
  const response = await passthrough(retry);
  setAuthCookies(response, { accessToken: refreshed.accessToken, refreshToken: refreshed.refreshToken }, { rotateCsrf: false });
  return response;
}
