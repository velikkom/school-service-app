# Güvenlik Tasarımı

## Kimlik doğrulama akışı (BFF + Backend)

```
Tarayıcı  --(HttpOnly cookie, same-origin)-->  Next.js BFF (route handlers, /app/api/*)
                                                     |
                                                     |  Authorization: Bearer <accessToken> (sunucu-sunucu)
                                                     v
                                                Spring Boot Backend
```

- JWT **tarayıcı JavaScript'ine hiçbir zaman açılmaz**. `localStorage`/`sessionStorage` kullanılmaz.
- BFF, backend'den aldığı `accessToken` ve `refreshToken`'ı iki ayrı **HttpOnly** cookie'ye yazar:
  - `ssp_at` (access token) — `HttpOnly`, `Secure` (production), `SameSite=Lax`, `Path=/`, TTL = access
    token ömrüyle eşit (15 dk, `JWT_ACCESS_TOKEN_TTL_MINUTES`).
  - `ssp_rt` (refresh token) — `HttpOnly`, `Secure` (production), `SameSite=Strict`, `Path=/api/auth`
    (sadece auth route'larına gönderilir), TTL 7 gün (`JWT_REFRESH_TOKEN_TTL_DAYS`).
  - Geliştirmede (`http://localhost`) `Secure` bayrağı kapalıdır (HTTPS olmadığı için), production'da
    zorunludur — ortam bazlı koşullu ayar, varsayılan kapatma değildir.
- **CSRF koruması**: durum değiştiren (`POST/PATCH/DELETE`) BFF route handler'ları, `SameSite` koruması
  yetmeyen senaryolara karşı ek olarak **double-submit CSRF token** kullanır: BFF login sonrası
  `ssp_csrf` adlı **HttpOnly olmayan** (okunabilir) bir token cookie'si set eder; frontend bu değeri
  `X-CSRF-Token` header'ında geri gönderir; BFF header ile cookie değerini karşılaştırır, eşleşmezse `403`.
  Bu kontrol GET isteklerinde uygulanmaz.
- BFF sadece önceden tanımlı backend path'lerine proxy yapar (allowlist, örn. `/api/students/**`); istemciden
  gelen keyfi hedef URL kabul eden açık bir proxy **yoktur**.
- **Logout**: BFF `ssp_at`, `ssp_rt`, `ssp_csrf` cookie'lerini temizler + backend'e refresh token'ı iptal
  ettirir (`POST /api/auth/logout`). Backend tarafında refresh token DB'de (hash olarak) tutulur ve
  `revokedAt` ile işaretlenir.
- **Oturum geçersizleştirme**: şifre değişikliği veya rol/isActive değişikliğinde, o kullanıcıya ait tüm
  refresh token kayıtları `revokedAt` ile iptal edilir. Access token'lar kısa ömürlü (15 dk) olduğundan en
  kötü durumda 15 dk içinde etkisiz hale gelir; kritik roller/isActive kontrolleri ayrıca her istekte
  (bir interceptor'da) kullanıcının güncel `isActive` durumunu DB'den doğrular.
- **Süresi dolan oturum**: access token süresi dolduğunda BFF otomatik `refresh` dener; refresh token da
  geçersizse kullanıcı login sayfasına yönlendirilir ve "Oturumunuzun süresi doldu, lütfen tekrar giriş
  yapın." mesajı gösterilir.

## "Beni hatırla" (remember-me)

`POST /api/auth/login` isteği artık opsiyonel bir `rememberMe: boolean` alanı kabul eder (default
`false`). Bu tercih SADECE refresh token'ın (ve onunla aynı kaderi paylaşan `ssp_rt`/`ssp_csrf`
cookie'lerinin) ömrünü etkiler — **access token ömrü (15 dk) her durumda sabittir**, remember-me
ile uzatılmaz.

- **Backend TTL**: `AuthService.login`, `rememberMe=false` ise `app.jwt.refresh-token-ttl-days`
  (varsayılan **1 gün**), `rememberMe=true` ise `app.jwt.refresh-token-ttl-days-remember-me`
  (varsayılan **30 gün**) kullanarak `refresh_token.expires_at`'ı hesaplar. `LoginResponse` artık
  gerçek `refreshTokenExpiresAt` (Instant) alanını da döner — BFF cookie `Max-Age`'ini bu değerden
  türetir, AYRI bir sabitle (drift riski) değil.
- **Cookie davranışı (BFF)**:
  - `rememberMe=false` → `ssp_rt` ve `ssp_csrf` **oturum çerezi** olarak set edilir (`Max-Age`/
    `Expires` YOK) — tarayıcı kapanınca silinir. `ssp_at` değişmez (her zaman 15 dk `Max-Age`).
  - `rememberMe=true` → `ssp_rt` ve `ssp_csrf`, backend'in döndüğü `refreshTokenExpiresAt`'tan
    hesaplanan `Max-Age` ile kalıcı çerez olarak set edilir (varsayılan ~30 gün).
  - **Refresh sırasında `ssp_rt` YENİDEN YAZILMAZ** (backend `refresh()` sadece yeni bir access
    token üretir, refresh token rotasyonu yapmaz — mevcut, değiştirilmemiş davranış). Bu sayede
    ilk login'de set edilen çerez özellikleri (oturum vs. kalıcı) refresh sonrasında da KORUNUR,
    ayrıca bir "tercih hatırlama" mekanizmasına gerek kalmaz.
- **Önemli, UI'da AÇIKÇA abartılmamalı**: tarayıcıların "oturumu geri yükle" özelliği (ör. "son
  oturumu aç" ayarı) nedeniyle bir oturum çerezi dahi bazı tarayıcılarda sekme/pencere kapatma
  sonrası hayatta kalabilir — bu yüzden "işaretlenmezse tarayıcı kapanınca KESİN çıkış" GARANTİSİ
  verilmez; sadece "daha kısa, sınırlı bir sunucu tavanı" sunulur (yukarıdaki 1 günlük tavan bu
  savunmayı sağlar).
- E-posta/şifre **asla** `localStorage`'a yazılmaz; JWT hiçbir zaman tarayıcı JS'ine açılmaz (bu
  dosyanın tepesindeki genel kural remember-me ile de DEĞİŞMEZ).

## Kayıt talebi ve ADMIN onayı

RBAC_MATRIX.md kural 1'in SINIRLI güncellemesi (bkz. docs/KNOWN_LIMITATIONS.md Karar Günlüğü,
2026-10-23): `POST /api/auth/register-requests` herkese açıktır ama **hiçbir zaman doğrudan bir
`app_user` oluşturmaz veya aktive etmez.**

- Request: `{ firstName, lastName, email, password, passwordConfirm }`. `role`/`isActive`/
  `approved` gibi alanlar DTO'da **yoktur** — istemciden gelse dahi Jackson tarafından yok sayılır
  (bilinmeyen alan hatası vermez, sessizce atlanır — `FAIL_ON_UNKNOWN_PROPERTIES=false` Spring Boot
  varsayılanı).
- `password`/`passwordConfirm` eşleşmeli (sunucu tarafında da doğrulanır, sadece istemci tarafında
  değil); şifre kuralı `ChangePasswordRequest`/`ResetPasswordRequest` ile AYNI (min 8 karakter).
- **Her zaman `202 Accepted`** döner (forgot-password ile aynı "var/yok ayrımı yapma" ilkesi):
  - E-posta zaten AKTİF bir `app_user`'a aitse: hiçbir `registration_request` oluşturulmaz/
    değiştirilmez, mevcut hesaba dokunulmaz (kilitlenmez, değişmez).
  - E-posta için zaten `PENDING_EMAIL_VERIFICATION` veya `REJECTED` durumda bir kayıt varsa: YENİ
    bir satır AÇILMAZ — mevcut satır sıfırlanır (ad/soyad/şifre güncellenir, durum
    `PENDING_EMAIL_VERIFICATION`'a döner), ÖNCEKİ doğrulama token'ları iptal edilir, YENİ bir
    doğrulama token'ı üretilip e-posta tekrar gönderilir (forgot-password'daki "yeni istekte eski
    token'lar iptal" deseniyle birebir aynı).
  - Aksi halde yeni bir `registration_request` satırı (`PENDING_EMAIL_VERIFICATION`) + doğrulama
    token'ı oluşturulur, doğrulama e-postası gönderilir.
- `POST /api/auth/register-requests/verify-email` `{ token }` — `password_reset_token` ile AYNI
  desen: SHA-256 hash karşılaştırma, tek kullanımlık (atomik `claim`), 24 saat TTL, genel
  ("token geçersiz veya süresi dolmuş") hata mesajı. Başarılı olursa `registration_request.status`
  → `EMAIL_VERIFIED` (henüz `app_user` OLUŞTURULMAZ — sadece ADMIN'e görünür/işlenebilir hale gelir).
- ADMIN onay akışı (`/api/registration-requests/**`, tamamı ADMIN-only):
  - `GET /api/registration-requests?status=&page=&size=` — liste (`status` ile filtrelenebilir;
    verilmezse TÜM durumlar döner — kesin sözleşme docs/API.md §18'dedir, burada sadece akış anlatılır).
  - `GET /api/registration-requests/{id}` — detay.
  - `POST /api/registration-requests/{id}/approve` `{ role, phone? }` — SADECE `status=EMAIL_VERIFIED`
    iken izinli (aksi halde `409` — "E-posta doğrulaması tamamlanmadan hesap etkinleşmesin" kuralı
    burada uygulanır). Yeni bir `app_user` oluşturur: `passwordHash` kayıt talebinden AYNEN
    kopyalanır (kullanıcı kendi seçtiği şifreyle giriş yapabilir, ikinci bir "geçici şifre" akışı
    YOKTUR), `role` ADMIN'in bu istekte belirttiği değerdir (kayıt talebinde `role` hiç yoktu).
    `registration_request.status` → `APPROVED`, `approvedAt`/`approvedByUserId`/`createdUserId` set
    edilir. Veli-öğrenci bağlantısı BURADA KURULMAZ — ADMIN, kullanıcı oluştuktan sonra mevcut
    `POST /api/parent-student-links` akışını AYRI bir adımda kullanır (bkz. docs/API.md §4).
  - `POST /api/registration-requests/{id}/reject` `{ reason? }` — `status=PENDING_EMAIL_VERIFICATION`
    veya `EMAIL_VERIFIED` iken izinli; `app_user` OLUŞTURULMAZ.
- Rate limit: `register-requests` ve `register-requests/verify-email`, `RateLimiterService` ile
  login/reset-password ile AYNI (IP+email, 5/15dk) sınırlamaya tabidir.
- Doğrulama e-postası yerelde Mailpit üzerinden doğrulanır (`PasswordResetMailService` ile AYNI
  desende yeni bir `RegistrationMailService`).

## Sosyal giriş (Google / Facebook / X) — mimari hazır, sağlayıcı bilgisi YOK

Bu ortamda GERÇEK bir Google/Facebook/X OAuth uygulaması (client ID/secret) tanımlı DEĞİLDİR.
Aşağıdaki mimari **çalışır durumda kodlanmıştır** ama ortam değişkenleri boşken her sağlayıcı
UI'da **devre dışı** görünür ve ilgili BFF route'ları çağrılırsa açık bir "sağlayıcı yapılandırılmamış"
hatası döner — ASLA sahte bir başarı göstermez.

- **Akış tamamen BFF'te** (Next.js route handler'ları) yürütülür, backend'e SADECE sağlayıcıdan
  doğrulanmış kimlik bilgisiyle (server-to-server) gelinir:
  1. `GET /api/auth/social/{provider}/start` — rastgele `state` (CSRF) + (PKCE destekleyen
     sağlayıcılar için, hepsinde) bir `code_verifier`/`code_challenge` üretir, ikisini de KISA ÖMÜRLÜ
     HttpOnly bir çerezde (`ssp_oauth_state`) saklar, sağlayıcının yetkilendirme URL'sine yönlendirir.
  2. `GET /api/auth/social/{provider}/callback` — dönen `state`'i çerezdekiyle karşılaştırır (eşleşmezse
     işlemi reddeder), `code`'u PKCE `code_verifier` ile sağlayıcının token endpoint'inde değiştirir,
     sağlayıcının userinfo endpoint'inden `{ providerUserId, email, emailVerified, fullName }` alır.
  3. BFF, backend'e `POST /api/auth/social-login` ile bu doğrulanmış bilgiyi iletir. Bu uç nokta
     SecurityConfig'te `permitAll` olduğu için (tarayıcıdan gelen istekleri JWT ile reddedemeyiz,
     henüz hiçbir token yok) "zaten BFF dışından çağrılmaz" varsayımına GÜVENİLMEZ — çağıranın
     gerçekten BFF olduğu `X-Internal-Service-Key` header'ı ile AYRICA doğrulanır (backend'deki
     `app.security.internal-service-key`/BFF'deki `INTERNAL_SERVICE_KEY` env değişkeni AYNI paylaşılan
     sır olmalı; `MessageDigest.isEqual` ile sabit-zamanlı karşılaştırılır; sır boşsa uç nokta HER
     ZAMAN 401 döner — fail-closed). Bu kontrol olmadan herkes doğrudan backend'e
     `emailVerified=true` + rastgele bir `providerUserId` göndererek e-posta doğrulamasını
     atlayabilir, hatta zaten bağlı bir `(provider, providerUserId)` çiftini tahmin ederek o
     kullanıcı olarak giriş yapabilirdi — backend kendi tarafında ayrıca provider+providerUserId
     eşleşmesini `oauth_identity` tablosuyla kontrol eder ama bu TEK BAŞINA yeterli değildir.
  4. Backend üç senaryodan birini döner:
     - Bu `(provider, providerUserId)` zaten bir `app_user`'a bağlıysa → NORMAL `login()` ile AYNI
       şekilde `accessToken/refreshToken/user` döner (BFF aynı `ssp_at`/`ssp_rt` cookie mekanizmasını
       kullanır — remember-me burada `false` kabul edilir, sosyal girişte "beni hatırla" checkbox'ı
       YOKTUR, varsayılan en kısa/güvenli tavan uygulanır).
     - Bu kimlik hiçbir yere bağlı değilse VE bu e-postayla AKTİF bir `app_user` da YOKSA → yeni bir
       `registration_request` (+ bu `oauth_identity`'yi ona bağlayan satır) oluşturur; sağlayıcı
       `emailVerified=true` dediyse `status=EMAIL_VERIFIED` (ADMIN direkt onaylayabilir), değilse
       `PENDING_EMAIL_VERIFICATION` (kendi doğrulama e-postamız gönderilir). Tarayıcıya token
       VERİLMEZ — "kayıt talebiniz alındı, onay bekleniyor" sonucu döner.
     - Bu e-postayla AKTİF bir `app_user` ZATEN VARSA (ve bu kimlik henüz ona bağlı değilse) →
       **otomatik bağlanmaz** (sadece aynı e-posta dönmesi yeterli KANIT sayılmaz — sağlayıcı
       e-postaları taklit/yanlış yapılandırma riski). Bunun yerine "bu e-postayla zaten bir
       hesabınız var, bağlamak için mevcut şifrenizle giriş yapın" sonucu döner; frontend kullanıcıyı
       `POST /api/auth/social-link` adımına yönlendirir.
  5. `POST /api/auth/social-link` (authenticated, kullanıcı ÖNCE normal şifresiyle login olmuş
     olmalı) `{ provider, providerUserId, email }` alır + principal'ın kimliği zaten `AuthenticatedUser`
     üzerinden doğrulanmıştır (ayrıca şifre tekrar İSTENMEZ — oturum zaten "hesabın kontrolü"
     kanıtıdır); `oauth_identity.user_id` principal'a bağlanır. Bu, "mevcut hesabın kontrolünü
     doğrulayan güvenli akış" şartını karşılar — rastgele bir OAuth girişiyle DEĞİL, aktif bir
     oturumla yapılır. BFF (`/api/auth/social-link` route handler'ı) bu çağrıyı backend'e iletmeden
     ÖNCE `GET /api/auth/me` ile şu an oturum açmış kullanıcının GERÇEK e-postasını `pending` çerezin
     e-postasıyla karşılaştırır (eşleşmezse 409) — aksi halde paylaşılan/kiosk bir bilgisayarda
     OAuth akışını başlatıp yarıda bırakan biri, o sosyal kimliğin DAHA SONRA o bilgisayarda oturum
     açan BAŞKA bir kullanıcının hesabına bağlanmasına (ve `pending` çerezin TTL'si — 10 dk —
     içinde bu kullanıcı farkında olmadan "sosyal giriş bağlandı" bildirimi almasına) yol açabilirdi.
- **Sağlayıcı yapılandırması** (`.env`): her sağlayıcı için `{PROVIDER}_OAUTH_CLIENT_ID`,
  `{PROVIDER}_OAUTH_CLIENT_SECRET` (örn. `GOOGLE_OAUTH_CLIENT_ID`). İkisi de boşsa o sağlayıcı
  "configured" sayılmaz: `GET /api/auth/social/providers` (BFF, public) hangi sağlayıcıların
  gerçekten kullanılabilir olduğunu döner, login/register sayfaları SADECE bu listedeki butonları
  aktif gösterir (diğerleri görünür ama `disabled` + kısa bir açıklama metniyle, sadece hover
  tooltip DEĞİL). `start`/`callback` route'ları yapılandırılmamış bir sağlayıcı için çağrılırsa
  `503` + açık Türkçe hata döner.
- Callback redirect URI'leri sabit/allowlist'tir (`{origin}/api/auth/social/{provider}/callback`),
  istemciden gelen bir `redirect_uri` parametresi KABUL EDİLMEZ.
- Provider secret'ları SADECE sunucu tarafı ortam değişkenlerinde tutulur, tarayıcıya hiçbir zaman
  gönderilmez (`NEXT_PUBLIC_*` ÖNEKİ KULLANILMAZ).

## Backend güvenlik sorumlulukları

- Spring Security: stateless JWT doğrulama (`Authorization: Bearer`), rol bazlı endpoint kuralları
  (`@PreAuthorize`) + servis seviyesinde nesne bazlı sahiplik kontrolü (route/trip/student/parent ilişkisi).
  Birden fazla controller'da tekrar eden basit rol kontrolleri (`hasRole`/`hasAnyRole`) için
  `com.okulservisi.backend.security.annotation` paketinde anlamlı, `@PreAuthorize`'ı meta-annotation
  olarak taşıyan özel annotation'lar kullanılır (`@AdminOnly`, `@AdminOrParent`,
  `@AdminDriverOrHostess`, `@AdminParentOrStudent`, `@AnyRole`, `@DriverOnly`, `@ParentOnly`) — sınıf/
  metot seviyesi öncelik davranışı Spring Security'nin kendi meta-annotation çözümlemesiyle birebir
  korunur. Nesne sahipliği/kullanıcı kimliği kullanan SpEL ifadeleri ve servis içindeki guard'lar
  (`StudentAccessGuard`, `TripAccessGuard`, `TrackingAccessGuard` vb.) bu genel annotation'larla
  DEĞİŞTİRİLMEZ — sadece saf rol kontrolleri için kullanılır. Her domain'in controller mapping
  yolları da kendi `<domain>.api.<Domain>ApiPaths` sabit sınıfında toplanır (bkz. docs/ARCHITECTURE.md).
- Parola hash: BCrypt (Spring `PasswordEncoder`, strength ≥ 10).
- JWT secret: ortam değişkeninden (`JWT_SECRET`) okunur; uygulama başlarken secret yoksa veya 32 byte'tan
  kısaysa **başlamayı reddeder** (fail-fast doğrulama).
- Reset password token: DB'de sadece SHA-256 hash'i saklanır, token'ın kendisi hiçbir log satırına
  yazılmaz. Token tek kullanımlık (`usedAt`), süreli (`expiresAt`, 30 dk), ve eşzamanlı çift kullanım
  DB seviyesinde `usedAt IS NULL` koşuluyla atomik `UPDATE ... WHERE usedAt IS NULL` ile önlenir (ilk isteği
  kazanan işler, ikincisi `410 Gone` benzeri genel hata alır). Yeni reset isteği gelirse, kullanıcının
  önceki **kullanılmamış** tüm token'ları otomatik iptal edilir (sadece en son token geçerli olur).
- `forgot-password` yanıtı kullanıcının var/yok olduğunu asla ayırt etmez (her durumda `202`).
- Login ve reset-password endpoint'lerinde IP+email bazlı deneme sınırlaması (örn. 5 deneme / 15 dk,
  aşılırsa `429`).
- Loglara parola, JWT, reset token, tam T.C. kimlik numarası yazılmaz (maskelenir).
- Production: Swagger UI (`SWAGGER_ENABLED=false` varsayılan), Actuator sadece `health` expose eder,
  diğer tüm endpoint'ler kapalıdır.
- CORS: sadece `CORS_ALLOWED_ORIGINS` içindeki origin'lere izin verilir (açık `*` yok).

## Yetkilendirme sorumluluk sınırı (BFF ↔ Backend)

- BFF: oturum/cookie yönetimi, CSRF, allowlist proxy, 401 → refresh akışı, 403/404'ü olduğu gibi iletme.
- Backend: **tüm** iş kuralı yetkilendirmesi (rol + nesne bazlı sahiplik). BFF hiçbir iş kuralı
  yetkilendirmesi yapmaz/varsaymaz — backend'in döndüğü karar nihaidir. Frontend route guard'ları sadece
  UX amaçlıdır.
