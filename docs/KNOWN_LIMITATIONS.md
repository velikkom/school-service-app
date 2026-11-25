# Bilinen Sınırlamalar ve Karar Günlüğü

## Karar Günlüğü

| Tarih | Konu | Karar | Gerekçe |
|---|---|---|---|
| 2026-10-15 | STUDENT rolü | Eklendi. 5 rol: ADMIN, DRIVER, HOSTESS, PARENT, STUDENT. STUDENT salt-okunur kendi kapsamı. | Kullanıcı netleştirme sorusuna yanıt. |
| 2026-10-15 | Docker engine kapalıydı | Docker Desktop uygulaması başlatıldı (sistem ayarı değiştirilmedi, mevcut kurulum çalıştırıldı). | Testcontainers/Compose için gerekli. |
| 2026-10-15 | `attendance_event` unique kısıtı | `UNIQUE(trip_id, student_id, event_type) WHERE is_correction=false` (partial index) uygulandı. | Dokümandaki "aynı sefer+öğrenci+tip tekrar edemez" ile "düzeltme yeni satır ekler" kurallarının birlikte sağlanabilmesi için. |
| 2026-10-15 | `parent_student_link.relation_type` | Enum/check kısıtı eklenmedi, serbest `varchar`. | Gereksinimde değer kümesi tanımlı değildi; gerçek değerler (anne/baba/vasi vb.) netleşince check constraint eklenecek. |
| 2026-10-23 | "Public register endpoint yoktur" kuralı (RBAC_MATRIX.md kural 1) | SINIRLI olarak güncellendi: `POST /api/auth/register-requests` herkese açık ama DOĞRUDAN kullanıcı oluşturmaz — sadece `registration_request` kaydı açar; e-posta doğrulaması + ADMIN onayı olmadan `app_user`'a dönüşmez, role/active istemciden asla kabul edilmez. | Kullanıcı talebi: profesyonel bir SaaS login/register deneyimi hedefi, kayıt talebi + yönetici onayı akışıyla. |
| 2026-10-23 | "Beni hatırla" | `app.jwt.refresh-token-ttl-days` varsayılanı 7→1 güne düşürüldü (işaretlenmediğinde kullanılan, tarayıcı oturum çerezinin arkasındaki sunucu tavanı); YENİ `app.jwt.refresh-token-ttl-days-remember-me` (varsayılan 30) eklendi. | Kullanıcı talebi: "seçilmediğinde sınırlı/oturum çerezi, seçildiğinde sunucuda tanımlı sınırlı kalıcı süre" — tek bir sabit TTL'nin iki farklı güvenlik profilini karşılayamaması. |
| 2026-10-23 | Sosyal giriş (Google/Facebook/X) | Mimari (oauth_identity tablosu, `/api/auth/social-login` + `/social-link`, BFF OAuth2+PKCE route'ları) eklendi; GERÇEK sağlayıcı client ID/secret'ı bu ortamda YOK — sağlayıcılar `.env`de değer tanımlanana kadar UI'da disabled görünür, gerçek bir OAuth akışı bu oturumda test EDİLEMEDİ. | Kullanıcı talebi: "yapılandırılmamış sağlayıcıyı aktifmiş gibi gösterme" — yapı hazır, etkinleştirme ortam değişkenine bağlı. |
| 2026-10-25 | Veli öz-kayıt (öğrenci kaydı sihirbazı) | v1: tek veli → tek öğrenci. "Kardeş Ekle" (bir başvuruda birden fazla öğrenci) YOK — ikinci/üçüncü çocuk ADMIN tarafından mevcut `/api/students`+`/api/parent-student-links` akışıyla eklenir. Yerlem referansındaki "okul seçin" adımı da alınmadı (tek firma, multi-tenant yok). Ayrıca `institution_settings.default_monthly_fee` ayarlanmadan bu akış `409` ile kapalı kalır. | Kullanıcı kararı (AskUserQuestion): kapsam sadeleştirmesi, hız/karmaşıklık dengesi. |

## Bilinen sınırlamalar (ilerleyen fazlarda güncellenecek)
> Bu bölüm geliştirme ilerledikçe docs/PROGRESS.md ile birlikte güncel tutulur. Aşağıdaki liste, projenin
> kapsam dışı bıraktığı veya bu oturumda tamamlanamayan kısımları şeffaf şekilde kaydeder.

- ~~2026-10-25: ADMIN'in `/ogrenciler/yeni` formu serbest metin `schoolName` kullanıyordu, §20'deki
  okul listesine dropdown'a GEÇİRİLMEMİŞTİ~~ ✅ ÇÖZÜLDÜ (2026-11-04) — `StudentCreateRequest`/
  `StudentUpdateRequest`'teki `schoolName` alanı `schoolId`'ye çevrildi, ADMIN formu artık §20'deki
  okul listesinden (aktif, `size=100`) bir `Form.Select` ile seçim yapıyor; backend seçilen okulun
  adını `schoolName`'e senkron yazıyor (EnrollmentService'teki aynı desen). Legacy veri (sadece
  serbest metin `schoolName`, `school_id=null`) DOKUNULMADAN kalır — PATCH'te `schoolId`
  gönderilmezse mevcut atama korunur.
- ~~2026-10-25: Kayıt talebi akışıyla `role=DRIVER` veya `role=HOSTESS` onaylanan kullanıcılar için
  bağlı `driver`/`hostess` satırı OLUŞTURULMAZ~~ ✅ ÇÖZÜLDÜ (2026-11-04) — `hostess`'in PARENT
  dışında hiçbir zorunlu ek alanı olmadığından HOSTESS için PARENT'la AYNI şekilde otomatik
  oluşturma eklendi. DRIVER için `POST /api/registration-requests/{id}/approve`'a opsiyonel
  `licenseNo` alanı eklendi (`driver.license_no` NOT NULL olduğu için) — kullanıcı kararı
  (AskUserQuestion): `role=DRIVER` seçilip `licenseNo` verilmezse approve isteği hiçbir satır
  yazılmadan `400` döner (Driver satırının hep eksiksiz oluşması tercih edildi, "sessizce
  oluşturulmasın" yerine). ADMIN onay ekranında rol=DRIVER seçilince zorunlu bir "Ehliyet numarası"
  alanı belirir.
- SMS entegrasyonu gerçek değildir; yalnızca ileride eklenebilecek bir arayüz/sınır noktası tanımlanmıştır.
- Harita sağlayıcısı seçimi ve lisans/attribution koşulları ilgili modül tamamlanırken netleştirilecektir.
- (Devam eden liste — bkz. PROGRESS.md "Açık sorunlar")
