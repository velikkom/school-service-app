import { NextResponse } from "next/server";
import { listConfiguredProviders } from "@/lib/auth/oauthProviders";

/**
 * GET /api/auth/social/providers — public. Hangi sosyal sağlayıcıların GERÇEKTEN yapılandırılmış
 * (env'de client id/secret dolu) olduğunu döner — login/register sayfaları SADECE bunları aktif
 * gösterir (bkz. docs/SECURITY.md "Sosyal giriş").
 */
export async function GET() {
  return NextResponse.json(listConfiguredProviders());
}
