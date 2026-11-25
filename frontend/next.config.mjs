/** @type {import('next').NextConfig} */
const nextConfig = {
  // Uygulama oturum çerezine bağlı, tamamen dinamik; deneysel önbellek özellikleri kapalı.
  reactStrictMode: true,
  poweredByHeader: false,
  // Sprint 3: izole E2E sunucusu (scripts/e2e.ps1) gunluk `next dev` ile ayni anda calisabilsin diye ayri
  // derleme klasoru kullanir. Ortam degiskeni yoksa Next.js varsayilani (.next) gecerlidir.
  ...(process.env.NEXT_DIST_DIR ? { distDir: process.env.NEXT_DIST_DIR } : {}),
  sassOptions: {
    // Ortak SCSS token'ları "@use 'tokens' as *;" ile her modülden erişilebilir.
    loadPaths: ["./src/styles"],
  },
};

export default nextConfig;
