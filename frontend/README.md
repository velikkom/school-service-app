# ServisFlow — Frontend (Sprint 2)

Next.js App Router + React + PrimeReact + SCSS. Tarayıcı backend'i doğrudan çağırmaz; tüm istekler bu
uygulamanın BFF route handler'larından (`src/app/api/**`) geçer ve token'lar HttpOnly çerezde tutulur.
Kurallar ve ortak parçalar: `../docs/design/SPRINT2_FRONTEND_GUIDE.md`.

## Gereksinimler

| Araç | Sürüm |
|---|---|
| Node.js | ≥ 20.19 (geliştirme makinesinde 24.21) |
| npm | 11.x (lockfile: `package-lock.json`) |
| Backend | http://localhost:8080 (bkz. kök README, `scripts/start-backend.ps1`) |

Seçilen paketler (`package.json`'da sabit): next 16.4.6, react / react-dom 19.3.1, primereact 10.9.9,
primeicons 8.0.2, sass 1.108.2, @tanstack/react-query 5.108.1, react-hook-form 7.92.0, zod 4.6.7,
@hookform/resolvers 7.0.7; test: vitest 5.1.1, @testing-library/react 16.3.3, jsdom 31.0.1,
@playwright/test 1.64.1; eslint 9.39.5 + eslint-config-next 16.4.6.

## Kurulum ve çalıştırma (PowerShell)

```powershell
cd frontend
Copy-Item .env.example .env.local   # yalnız ilk kurulumda; BFF_COOKIE_SECRET'i doldurun
npm ci
npm run dev                          # http://localhost:3000
```

Production derlemesi:

```powershell
npm run build
npm run start
```

### Ortam değişkenleri (`.env.local`, git'e eklenmez)

| Değişken | Açıklama |
|---|---|
| `BACKEND_INTERNAL_URL` | BFF'in backend adresi (varsayılan `http://localhost:8080`) |
| `JWT_ACCESS_TOKEN_TTL_MINUTES`, `JWT_REFRESH_TOKEN_TTL_DAYS` | Çerez süreleri (backend ile aynı) |
| `BFF_COOKIE_SECRET` | CSRF token imza sırrı |
| `OAUTH_REDIRECT_BASE_URL`, `INTERNAL_SERVICE_KEY`, `*_OAUTH_CLIENT_ID/SECRET` | Sosyal giriş (boşsa butonlar gizli) |

## Testler

```powershell
npm run lint                         # ESLint (tüm proje)
npm run test                         # Vitest birim/bileşen testleri (tests/unit/**)
```

### E2E (Sprint 3 — yalnız izole ortam)

Playwright paketi günlük geliştirme ortamına (PostgreSQL 5434, backend 8080, frontend 3000) **bağlanmaz**. Depo
kökünden tek komut:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\e2e.ps1            # 1 temiz koşu
powershell -ExecutionPolicy Bypass -File scripts\e2e.ps1 -Runs 2    # arka arkaya 2 temiz koşu
powershell -ExecutionPolicy Bypass -File scripts\e2e.ps1 -Grep "s3-finance"   # tek senaryo
```

Betik her koşuda `compose.e2e.yaml` ile tek kullanımlık `ssp-e2e` ortamını (tmpfs PostgreSQL `ssp_e2e` 127.0.0.1:55434,
Mailpit 127.0.0.1:11025/18025) kurar, backend jar'ını 18080'de yalnız bu veritabanıyla başlatır, yazma testlerinden
önce hedefi doğrular (koşuya özel ADMIN yalnız `ssp_e2e`'de var + backend o hesapla giriş yapabiliyor; aksi halde
çıkış kodu 2), Next.js'i 3100'de ayrı `.next-e2e` klasörüyle açar, Playwright'ı çalıştırır ve sonunda yalnız
`ssp-e2e` kaynaklarını kaldırır. Sırlar (DB/JWT/ADMIN) her koşuda rastgele üretilir ve yazdırılmaz. Geliştirme
veritabanından yalnız salt-okunur satır sayıları alınıp koşu öncesi/sonrası karşılaştırılır; rapor
`.e2e\last-report.json`. Ortam değişkenleri olmadan `npx playwright test` global setup'ta durur.

Gerekenler: Docker Desktop, JDK 17, Node 20+. Değişen ekranların 1440/390/360 ekran görüntüleri:
`$env:E2E_SCREENS='1'; powershell -ExecutionPolicy Bypass -File scripts\e2e.ps1 -SkipBuild -Grep s3-screens`
(çıktı: `docs/design/sprint3-screens/`).

## Klasör yapısı

```
src/
  app/
    layout.jsx            kök layout: fontlar, PrimeReact teması, sağlayıcılar
    page.jsx              / → oturum varsa /dashboard, yoksa /login
    (public)/             login, kayit-talebi, e-posta-dogrula, sifremi-unuttum, sifre-sifirla
    (protected)/          oturumlu ekranlar; layout = rol kabuğu + doğrudan URL erişim kontrolü
    api/                  BFF: auth/* + domain başına allowlist proxy ([[...path]]/route.js)
  proxy.js                Next 16 "proxy" (eski middleware): oturum yönlendirmesi (UX)
  features/<özellik>/     service/ (API), hooks/ (sorgu + anahtarlar), action/ (mutasyonlar),
                          schemas/ (Zod), components/ (…Screen.jsx), style/ (*.module.scss)
    auth, trips, attendance, attendance-corrections, daily-service, tracking, students, family,
    enrollment, stop-change-requests, operations, requests, registration-requests, notifications,
    reports, settings, parents, drivers, hostesses, vehicles, schools, routes, assignments, users,
    contracts, billing, payments
  components/
    layout/               AppShell (kenar menü, alt menü, Daha fazla), ProtectedShell, PageMeta, RoleRedirect
    feedback/             ScreenState/QueryState, AppDialog, ReasonDialog, toast/confirm
    ui/                   StatusBadge, VehiclePlate, Card/MetricCard, Notice/Avatar, Field, LocationPanel, ChildSwitcher
    data/                 ManagementList (yönetim liste/form şablonu) ve yardımcıları
  lib/
    http/                 client (CSRF + 401'de tek seferlik refresh), apiError, page, document (makbuz/ekstre)
    auth/                 BFF sunucu tarafı: çerezler, CSRF, refresh, proxy fabrikası, safeRedirect, OAuth
    rbac/                 menüler (SCREEN_MATRIX §7), route erişim kuralları, rol ana ekranları
    formatting/           tarih (Europe/Istanbul, tr-TR), para (TRY), enum etiketleri
  providers/              AppProviders (Query + PrimeReact tr locale), AuthProvider (rol değişiminde önbellek temizliği)
  styles/
    _tokens.scss          kırılım mixin'leri
    tokens/_variables.scss  CSS değişkenleri (renk, tipografi, boşluk)
    base/                 temel stiller, PrimeReact tema uyarlaması
    globals.scss
tests/unit/               Vitest (field, family, admin-ops, management)
e2e/                      Playwright akışları + seed/smoke araçları
```
