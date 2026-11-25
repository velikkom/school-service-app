# API Sözleşmesi (docs/API.md)

> Bu dosya **tek doğruluk kaynağıdır**. Backend bu sözleşmeye göre uygulanır, Frontend bu sözleşmeye göre
> tüketir, Test Agent bu sözleşmeye göre doğrular. Değişiklik gerekiyorsa: ihtiyacı koordinatöre bildir →
> etkilenen taraflar belirlenir → bu dosya güncellenir → ilgili agent'lara görev verilir.

## Genel kurallar

- Base path: `/api`
- Format: JSON, UTF-8. Tarih/saat: ISO-8601. Olay zamanları `Instant` (UTC) olarak taşınır, UI
  `Europe/Istanbul` saatine çevirerek gösterir. İş günü / dönem alanları (`businessDate`, `period`)
  `LocalDate` / `YearMonth` (`yyyy-MM`) olarak taşınır.
- Para: JSON'da **string** olarak taşınır (örn. `"1250.00"`), backend `BigDecimal(precision=12,scale=2)`
  kullanır. Para birimi sabit `TRY`.
- Kimlik doğrulama: Next.js BFF, backend'e `Authorization: Bearer <JWT>` ile istek atar. Tarayıcı backend'i
  **doğrudan** çağırmaz; sadece BFF'in aynı origin proxy yollarını (`/api/...` Next.js tarafında) çağırır.
- Yetkisiz istek: `401 Unauthorized`. Yetkisi olmayan ama kimliği doğrulanmış istek: `403 Forbidden`.
  Bulunamayan/kapsam dışı kaynak: `404 Not Found` (varlığı ifşa etmemek için, kapsam dışı ID'lerde de 404
  döner — 403 değil, "bu ID var ama yetkin yok" bilgisi sızdırılmaz).

### Hata response formatı (RFC 7807 esintili, sabit şema)

```json
{
  "timestamp": "2026-10-15T10:30:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Girilen bilgiler geçersiz.",
  "path": "/api/students",
  "fieldErrors": [
    { "field": "tcNo", "message": "T.C. kimlik numarası 11 hane olmalıdır." }
  ]
}
```
- `fieldErrors` sadece validation hatalarında bulunur, yoksa alan hiç dönmez.
- Stack trace, SQL, iç sınıf adı **asla** dönmez.

### Pagination (tüm liste endpoint'lerinde ortak)

Query: `page` (0-based, default 0), `size` (default 20, max 100), `sort` (örn. `sort=lastName,asc` —
sadece her endpoint'in izin verdiği alan adlarıyla, aksi halde `400`).

Response zarfı:
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 134,
  "totalPages": 7
}
```

### Enum sözlüğü (seçilmiş)
- `Role`: `ADMIN`, `DRIVER`, `HOSTESS`, `PARENT`, `STUDENT`
- `RouteDirection`: `MORNING`, `AFTERNOON`
- `TripStatus`: `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`
- `AttendanceEventType`: `BOARDING`, `DROPOFF`, `ABSENCE`
- `ContractStatus`: `ACTIVE`, `CANCELLED`, `EXPIRED`, `PENDING_APPROVAL`
- `Gender` (öğrenci, opsiyonel): `MALE`, `FEMALE`
- `PaymentMethod`: `CASH`, `BANK_TRANSFER`, `CARD`, `OTHER`
- `PaymentStatus`: `COMPLETED`, `CANCELLED`
- `NotificationChannel`: `IN_APP`, `EMAIL`

---

## 1. Auth (`/api/auth`)

| Method | Path | Açıklama | Rol |
|---|---|---|---|
| POST | `/login` | email+şifre ile giriş, access+refresh token döner (BFF bunları HttpOnly cookie'ye yazar) | public |
| POST | `/refresh` | refresh cookie ile yeni access token | authenticated (cookie) |
| POST | `/logout` | mevcut refresh token'ı/oturumu iptal eder | authenticated |
| GET | `/me` | giriş yapan kullanıcının profil+rol bilgisi | authenticated |
| POST | `/change-password` | `currentPassword`, `newPassword` — başarılı olursa diğer tüm oturumlar iptal edilir | authenticated |
| POST | `/forgot-password` | `{ "email": "..." }` — kullanıcı var/yok fark etmeksizin **her zaman** `202 Accepted` | public |
| POST | `/reset-password` | `{ "token": "...", "newPassword": "..." }` — token tek kullanımlık, süreli | public |
| POST | `/register-requests` | `{ firstName, lastName, email, password, passwordConfirm }` — kayıt TALEBİ açar, doğrudan kullanıcı OLUŞTURMAZ; her zaman `202 Accepted` | public |
| POST | `/register-requests/verify-email` | `{ "token": "..." }` — e-posta doğrulama, token tek kullanımlık/süreli (24 saat) | public |
| GET | `/social/providers` | Hangi sosyal sağlayıcıların (`GOOGLE`/`FACEBOOK`/`X`) gerçekten yapılandırılmış (env'de client id/secret var) olduğunu döner | public (BFF) |
| GET | `/social/{provider}/start` | OAuth2+PKCE akışını başlatır, sağlayıcıya yönlendirir | public (BFF) |
| GET | `/social/{provider}/callback` | Sağlayıcıdan dönüşü işler → login VEYA yeni kayıt talebi VEYA "mevcut hesaba bağlan" sonucu | public (BFF) |
| POST | `/social-login` | BFF→backend (server-to-server): doğrulanmış sosyal kimlik bilgisiyle login/kayıt-talebi/bağlama kararı. `X-Internal-Service-Key` header'ı ZORUNLU (yoksa/yanlışsa `401` — bkz. SECURITY.md) | internal (bkz. SECURITY.md) |
| POST | `/social-link` | Oturum açmış kullanıcının kendi hesabına yeni bir sosyal kimlik bağlaması | authenticated |

`POST /login` request: `{ "email": "a@b.com", "password": "...", "rememberMe": false }` (`rememberMe` opsiyonel, varsayılan `false`)
`POST /login` response: `{ "accessToken": "...", "refreshToken": "...", "refreshTokenExpiresAt": "2026-...Z", "user": { "id", "fullName", "email", "role" } }`
(Not: Bu alanlar backend→BFF arasında taşınır; BFF tarayıcıya sadece `Set-Cookie: HttpOnly` ile oturum
kurar, token'ları JSON body olarak tarayıcıya **asla** iletmez. `refreshTokenExpiresAt`, BFF'in
`rememberMe=true` iken çerez `Max-Age`'ini hesaplamak için kullandığı TEK doğruluk kaynağıdır — ayrı
bir sabit DEĞİL. Detay: docs/SECURITY.md "Beni hatırla".)

`POST /refresh` request: `{ "refreshToken": "..." }` → response: `{ "accessToken": "..." }` (refresh
token ROTASYONU YAPILMAZ — mevcut, değişmeyen davranış; bu sayede login'de set edilen çerez
kalıcılığı/remember-me tercihi refresh sonrasında da korunur.)
`POST /logout` request: `{ "refreshToken": "..." }` → response: `204 No Content` (ilgili refresh token DB'de iptal edilir)

Login/forgot-password/reset-password/register-requests(+verify-email) endpoint'leri **rate limit**'e
tabidir (bkz. SECURITY.md). `register-requests` ve `verify-email` detaylı davranışı (tekrarlı talep
ele alma, mevcut hesap koruması) için bkz. docs/SECURITY.md "Kayıt talebi ve ADMIN onayı"; ADMIN'in
talepleri onaylama/reddetme uç noktaları için bkz. aşağıda §18.

---

## 2. Kullanıcı yönetimi (`/api/users`) — sadece ADMIN

| Method | Path | Açıklama |
|---|---|---|
| GET | `/api/users` | sayfalı liste, filtre: `role`, `isActive`, `q` (ad/email arama), `studentLinked` (Sprint 3, bkz. §3 "Öğrenci hesabı bağı") |
| GET | `/api/users/{id}` | detay |
| POST | `/api/users` | yeni kullanıcı oluştur (ADMIN/DRIVER/HOSTESS/PARENT/STUDENT) — `role` zorunlu, sunucu geçici şifre üretir veya ADMIN belirler, `passwordHash` istemciden alınmaz. **Bilinçli, korunan iki-aşamalı akış** (2026-11-06): bu uç DRIVER/HOSTESS/PARENT için domain profili OLUŞTURMAZ (Veli/Şoför/Hostes formlarındaki "+ Yeni kullanıcı oluştur" iç bileşeni — `UserPicker` — bu ucu SADECE hesap oluşturmak için çağırır, profili DIŞ formun kendi `POST /api/{parents\|drivers\|hostesses}` çağrısı tamamlar; `licenseNo`/`tcNo` gibi alanlar bu iç adımda henüz toplanmamıştır). Bu yüzden bu ucun sözleşmesi DEĞİŞTİRİLMEDİ. "Kullanıcı Yönetimi" ekranının ADMIN'e doğrudan DRIVER/HOSTESS/PARENT oluşturturken bıraktığı profilsiz-kullanıcı boşluğu, aşağıdaki `POST /api/users/{id}/role` ucunun hesap oluşturulduktan HEMEN SONRA aynı rolle (idempotent "ensure") çağrılmasıyla kapatılır — frontend `CreateUserModal` bunu otomatik yapar. |
| PATCH | `/api/users/{id}` | ad/telefon/isActive günceller. **`role` artık bu uçtan KABUL EDİLMEZ** (2026-11-06 — aşağıya bkz.): istekte `role` doluysa `400 BAD_REQUEST` döner. |
| **POST** | **`/api/users/{id}/role`** | **(YENİ, 2026-11-06)** Rol değişikliğini — DRIVER/HOSTESS/PARENT hedefleniyorsa gerekli domain profilinin oluşturulması/yeniden kullanılmasıyla BİRLİKTE — TEK bir veritabanı transaction'ında uygular. Body: `{ role, licenseNo? }`. `licenseNo`: SADECE `role=DRIVER` VE kullanıcının henüz bir Driver profili YOKSA zorunludur (`400` döner). DRIVER/HOSTESS/PARENT hedeflendiğinde: mevcut bir profil varsa (reuse) yeniden kullanılır — DRIVER/HOSTESS için pasifse aktive edilir, mükerrer satır OLUŞTURULMAZ (DB'de `uq_driver_user_id`/`uq_hostess_user_id`/`uq_parent_user_id` kısıtlarıyla da ayrıca korunur); yoksa oluşturulur. Profil adımı HERHANGİ bir sebeple başarısız olursa (örn. eksik `licenseNo`) rol ataması DAHİL **hiçbir değişiklik commit edilmez** (gerçek DB transaction rollback — frontend'in telafi edici bir "geri PATCH" çağrısı yapmasına GEREK YOKTUR ve artık YAPILMAZ). İstek, hedef rol kullanıcının MEVCUT rolüyle AYNI olsa bile profil adımını çalıştırır (idempotent "ensure" semantiği — bkz. yukarıdaki `POST /api/users` notu); son-aktif-ADMIN koruması ve oturum iptali SADECE rol GERÇEKTEN değişiyorsa uygulanır. Aynı kullanıcıya eşzamanlı çağrılar kullanıcı satırı üzerinde `SELECT ... FOR UPDATE` ile serileştirilir (Payment/Student modülleriyle AYNI desen). Geçmiş domain ilişkileri (sefer, ödeme, öğrenci bağlantısı vb.) HİÇBİR ŞEKİLDE silinmez/deaktive edilmez. |
| POST | `/api/users/{id}/deactivate` | pasife al — son aktif ADMIN ise `409` |
| POST | `/api/users/{id}/activate` | aktive et |

Request/response DTO'ları `passwordHash`, `createdAt`, `createdBy` gibi sistem alanlarını içermez/kabul etmez.

**2026-11-06 — rol değişikliği backend'de atomikleştirildi (Sprint 1 inceleme sonrası 3. tur):**
Önceki sürümde frontend "PATCH role → POST profil → hata halinde PATCH eski role" şeklinde bir
telafi (compensating action) dizisi uygulayıp bunu "atomik işlem" olarak adlandırıyordu — bu GERÇEK
veritabanı atomikliği SAĞLAMIYORDU (tarayıcı kapanabilir, ağ kesilebilir, geri alma isteği kendisi
başarısız olabilir; backend doğrudan çağrıldığında frontend kontrolü tamamen atlanırdı). Çözüm:
yukarıdaki `POST /api/users/{id}/role` ucu eklendi, rol+profil mantığı `UserService#changeRole`
içinde TEK bir `@Transactional` metoda taşındı, `PATCH /api/users/{id}` üzerinden rol değiştirme
KAPATILDI (bypass imkânsız hale getirildi). Gerçek PostgreSQL'e karşı entegrasyon testleriyle
kanıtlandı — bkz. docs/TEST_PLAN.md "Sprint 1 — backend atomiklik" ve docs/SPRINT_01_REPORT.md.

---

## 3. Öğrenci (`/api/students`)

- `GET /api/students` (ADMIN: tümü; PARENT: sadece bağlı çocukları; STUDENT: sadece kendisi) — filtre:
  `q`, `routeId`, `isActive`
- `GET /api/students/{id}` — nesne bazlı kontrol
- `POST /api/students` (ADMIN) — `fullName`, `birthDate`, `tcNo` (opsiyonel, config ile zorunlu olabilir),
  `schoolId` (opsiyonel — §20'deki `school` listesinden seçilir; geçersiz/yok bir id `400` döner),
  `grade`. 2026-11-04'ten önce bu alan serbest metin `schoolName` idi (ADMIN formu dropdown'a
  GEÇİRİLDİ); backend seçilen okulun adını `schoolName`'e otomatik senkronlar — `schoolName` artık
  istemciden DOĞRUDAN kabul edilmez (sadece `StudentResponse`'da geriye dönük uyum için okunur alan
  olarak kalır).
- `PATCH /api/students/{id}` (ADMIN) — `schoolId` aynı şekilde çalışır; `null`/gönderilmezse mevcut
  okul ataması KORUNUR (diğer PATCH alanlarıyla aynı "null = dokunulmadı" kuralı — bu endpoint'ten
  okul ataması TEMİZLENEMEZ, sadece değiştirilebilir).
- `StudentResponse`'a §19/§20 ile `schoolId` eklendi (nullable — veli öz-kayıt sihirbazından VEYA
  ADMIN'in dropdown'dan okul seçtiği öğrencilerde dolu; hiçbiri olmadıysa `null`).
- `POST /api/students/{id}/deactivate` (ADMIN) — soft deactivate (silme yok)

### Öğrenci hesabı bağı (Sprint 3 — 2026-11-24)

Öğrencinin kendi giriş hesabı (`role=STUDENT`) ile öğrenci kaydı arasındaki 1-1 bağ (`student.user_id`, V4'teki
`uq_student_user_id` kısmi tekil indeksiyle korunur; yeni migration yok). Hesap mevcut akışla oluşturulur
(`POST /api/users`, `role=STUDENT`), ardından bu uçla bağlanır.

| Method | Path | Açıklama |
|---|---|---|
| PUT | `/api/students/{id}/account` | (ADMIN) Body `{ userId }`. Başarıda `200 StudentResponse` (`userId` dolu). Aynı çift zaten bağlıysa değişiklik yapmadan `200` (güvenli tekrar). |
| DELETE | `/api/students/{id}/account` | (ADMIN) Bağı kaldırır → `200 StudentResponse` (`userId: null`). Bağ yoksa değişiklik yapmadan `200`. Hesap silinmez; hesabın refresh token'ları iptal edilir. |

Kurallar ve hata kodları (gövde her zaman genel hata şemasıdır):
- `userId` eksik → `400 VALIDATION_ERROR`; kullanıcı yok → `400 BAD_REQUEST`; rolü STUDENT değil → `400`;
  hesap pasif → `400`; öğrenci kaydı pasif → `400`; öğrenci yok → `404`.
- Öğrenci başka bir hesaba bağlı → `409 CONFLICT` (var olan bağ sessizce değiştirilmez; önce `DELETE`).
- Hesap başka bir öğrenciye bağlı → `409 CONFLICT`.
- Eşzamanlı istekler kullanıcı → öğrenci sırasıyla `SELECT ... FOR UPDATE` ile serileştirilir; kaybeden taraf
  `409` alır. Tekil indeks ihlali olursa `409 DATA_INTEGRITY_VIOLATION`.
- Bağ kurma/kaldırma `audit_log`'a `STUDENT_ACCOUNT_LINKED` / `STUDENT_ACCOUNT_UNLINKED` olarak yazılır.
- İlgili davranışlar: STUDENT kapsamı (`GET /api/students`, `/daily-service`, `/attendance`, atamalar) her istekte
  bu bağdan türetilir. `POST /api/users/{id}/role`: bağlı bir STUDENT hesabının rolü değiştirilmek istenirse
  `409` (önce bağı kaldırın); aynı role "ensure" çağrısı etkilenmez. `POST /api/users/{id}/deactivate`: bağ
  korunur, hesap giriş yapamaz.
- `GET /api/users` yeni filtre: `studentLinked=true|false` (bir öğrenci kaydına bağlı olan / olmayan hesaplar).
  ADMIN arayüzü bağlanabilir hesapları `role=STUDENT&isActive=true&studentLinked=false` ile listeler.

## 4. Veli (`/api/parents`) ve öğrenci bağlantıları (`/api/parent-student-links`)

- `GET/POST/PATCH /api/parents` (ADMIN); PARENT kendi profilini `GET /api/parents/me`
- `POST /api/parent-student-links` `{ parentId, studentId, relationType }` — aynı çift tekrar edemez (unique),
  ADMIN only
- `GET /api/parent-student-links?studentId=&parentId=` (ADMIN) — en az biri verilmeli; aktif+pasif
  (revoke edilmiş) tüm bağlantıları döner (liste zarfı olmadan düz dizi, filtre zaten dar kapsamlı).
- `POST /api/parent-student-links/{id}/revoke` — ilişkiyi pasife al (silme yok, geçmiş erişim kaydı kalır)

## 5. Araç (`/api/vehicles`)

- CRUD (ADMIN). Plaka normalize+unique. `capacity` zorunlu pozitif tam sayı.
- DRIVER: `GET /api/vehicles/mine` — sadece aktif atandığı araç.

## 6. Şoför / Hostes (`/api/drivers`, `/api/hostesses`)

- CRUD (ADMIN). Her biri bir `User` (role=DRIVER/HOSTESS) ile 1-1 ilişkilidir (`userId` FK, unique).
- `GET /api/drivers/me`, `GET /api/hostesses/me` — kendi mesleki profili.

## 7. Güzergâh / Durak (`/api/routes`, `/api/routes/{id}/stops`)

- `Route`: `name`, `direction` (MORNING/AFTERNOON).
- `Stop`: `routeId`, `name`, `sequenceNo` (benzersiz sıra, route içinde unique), `plannedTime`.
- Create/update/delete ADMIN-only (Route VE Stop). `GET` (liste/detay) DRIVER/HOSTESS'e de açık —
  sadece kendi atandığı route'u VE o route'un duraklarını salt-okunur görür (bir route'un "atanmış"
  sayılması: o personelin en az bir `Trip`'inin bu route'u kullanması). Kapsam dışı erişim 404 döner
  (403 değil). (2026-10-24: Stop GET'leri DRIVER/HOSTESS'e açıldı, bkz. değişiklik günlüğü.)

## 8. Öğrenci atamaları (`/api/assignments`)

- `{ studentId, routeId, stopId, direction, validFrom, validTo? }`
- Aynı öğrenci + aynı yön + çakışan tarih aralığında birden fazla **aktif** atama yasak (overlap kontrolü).
- ADMIN CRUD; PARENT/STUDENT salt-okunur kendi kapsamında.

## 9. Sefer (`/api/trips`)

- `{ routeId, vehicleId, driverId, hostessId?, businessDate, direction, plannedStartTime? }` (`status` her zaman
  sunucuda `PLANNED` başlar). `plannedStartTime` ("HH:mm[:ss]", Europe/Istanbul yerel saat) 2026-11-18'de eklendi.
- Aynı araç/şoför/hostes aynı `businessDate`+`direction`'da birden fazla sefere atanamaz (`409`). Hostes
  kontrolü 2026-11-18'de eklendi (önceden sadece araç/şoför kontrol ediliyordu).
- `POST /api/trips/{id}/start`, `/complete` — ADMIN + atanmış DRIVER/HOSTESS (değişmedi).
- `POST /api/trips/{id}/cancel` — **2026-11-18'den itibaren SADECE ADMIN** (DRIVER/HOSTESS `403`; bunun yerine
  §23'teki iptal TALEBİNİ açarlar). Body opsiyonel `{ reason }` → `cancellationReason`. Veli/öğrenci ve
  personele bildirim (§21 "Bildirimler").
- `PATCH /api/trips/{id}` (ADMIN, YENİ) — bkz. §21.
- `TripResponse` (2026-11-18): `{ id, routeId, routeName, vehicleId, vehiclePlate, driverId, driverFullName,
  hostessId, hostessFullName, businessDate, direction, status, plannedStartTime, startedAt, completedAt,
  version, cancellationReason, openCancellationRequestId }` — `openCancellationRequestId` sadece
  `GET /api/trips/{id}` yanıtında ADMIN/DRIVER/HOSTESS için dolu olabilir. Plaka alanı, araç uç noktasına
  erişimi olmayan HOSTESS'in sefer kartında plakayı görebilmesi içindir.
- DRIVER/HOSTESS: `GET /api/trips/mine?businessDate=...`
- `GET /api/trips/{id}/roster` (ADMIN tümü; DRIVER/HOSTESS sadece kendi atandığı sefer, aksi halde
  `404`; PARENT/STUDENT erişemez — kendi kapsamları zaten `GET /api/students/{id}/attendance` ile
  karşılanıyor) — seferin güzergâh+yön+`businessDate`'ine göre aktif atanmış TÜM öğrencilerin listesi,
  durak sırasına (`sequenceNo`) göre sıralı, her biri güncel yoklama durumuyla: → `[{ studentId,
  studentFullName, stopId, stopName, sequenceNo, status }]` (`status`: `NONE`/`BOARDED`/
  `DROPPED_OFF`/`ABSENT`). 2026-11-18: her satıra `stopPlannedTime`, `absenceNotice` (aktif veli "servise
  binmeyecek" bildirimi `{ id, note, createdAt, parentFullName }` veya `null` — resmi yoklama DEĞİLDİR) ve
  `pendingCorrectionId` (bekleyen yoklama düzeltme talebi id'si veya `null`) eklendi. DRIVER/HOSTESS'in manuel öğrenci ID girmek zorunda kalmadan gerçek
  yolcu listesini görebilmesi için eklendi (Faz F'den kalma bilinen kapsam boşluğu, Faz I'de kapatıldı).

## 10. Biniş/İniş/Devamsızlık (`/api/trips/{tripId}/attendance-events`)

- `POST .../boardings` `{ studentId }`, `POST .../dropoffs` `{ studentId }`, `POST .../absences` `{ studentId, reason? }`
- Kural: iniş, o öğrenci için o seferde önceden biniş kaydı olmadan kabul edilmez. Aynı sefer+öğrenci+tip
  tekrar edemez (unique). Devamsızlık kaydı varsa aynı öğrenci için aynı sefere biniş eklenemez (ve tersi) —
  çelişki `409`. İşlemi yapan personel o sefere atanmamışsa `404` (kapsam dışı).
- `GET /api/students/{id}/attendance?from=&to=` — nesne bazlı kontrol.

## 11. Sözleşme (`/api/contracts`)

- `GET /api/contracts?studentId=&status=` (ADMIN: tümü; PARENT: sadece bağlı çocukları)
- `GET /api/contracts/{id}` — nesne bazlı kontrol
- `POST /api/contracts` (ADMIN) `{ studentId, monthlyFee, startDate, endDate?, discountAmount? }`
- `PATCH /api/contracts/{id}` (ADMIN)
- `POST /api/contracts/{id}/cancel` (ADMIN) — state machine (bkz. kod), geçmiş silinmez
- Aynı öğrenci için tarih aralığı çakışan iki **ACTIVE** sözleşme yasak.
- `status` değerleri: `ACTIVE`/`CANCELLED`/`EXPIRED`/`PENDING_APPROVAL` (son değer §19'daki veli
  öz-kayıt akışına özel, bkz. aşağıda `POST /api/contracts/{id}/approve`, `/reject`).
- `ContractResponse` artık `studentFullName` alanı da içerir (salt-okunur, görüntüleme amaçlı —
  `studentId` zaten birincil referans). 2026-10-25 eklendi: ADMIN'in §19 onay ekranında hangi
  öğrenciye ait talebi onayladığını görebilmesi için (önceden sadece `studentId` dönüyordu, ekranda
  "Öğrenci #13" gibi anlamsız bir metin gösteriliyordu).

## 12. Aylık ücret / tahakkuk (`/api/billing/charges`, `POST /api/billing/run`)

- `POST /api/billing/run` `{ period: "2026-10" }` (ADMIN, idempotent — aynı dönem+öğrenci için ikinci
  tahakkuk oluşturmaz, var olanı değiştirmeden `skipped` sayar). Ayrıca scheduler ile otomatik (Europe/Istanbul,
  ayın 1'i 03:00) tetiklenir — aynı idempotency kuralına uyar.
- `GET /api/billing/charges?studentId=&period=`

## 13. Ödeme (`/api/payments`)

- `GET /api/payments?studentId=` (ADMIN: tümü; PARENT: sadece kendi çocuğunun ödeme geçmişi)
- `GET /api/payments/{id}` — nesne bazlı kontrol
- `POST /api/payments` `{ studentId, amount, method, note? }` — kalan borçtan fazla ödeme `409`. Aynı
  idempotency key (header: `Idempotency-Key`) ile tekrar istek aynı ödemeyi döner, ikinci kayıt oluşturmaz.
- `POST /api/payments/{id}/cancel` — iptal, geçmiş silinmez, bakiye yeniden hesaplanır.
- `GET /api/students/{id}/statement` — borç/ödeme/bakiye kronolojik ekstre.

## 14. Dashboard / Rapor (`/api/reports/**`) — ADMIN only
- `GET /api/reports/operations/daily-summary?date=` → `{ businessDate, totalTrips, plannedCount,
  inProgressCount, completedCount, cancelledCount, boardingCount, dropoffCount, absenceCount }`
- `GET /api/reports/finance/collections?from=&to=` (tarihler dahil/inclusive, SADECE `status=COMPLETED`
  ödemeler) → `{ from, to, totalCollected, byMethod: [{ method, amount }] }`
- `GET /api/reports/finance/receivables` → `{ totalCharged, totalCollected, totalReceivable }`
  (öğrenci bazında kırılım YOK — kasıtlı, bkz. `GET /api/students/{id}/statement`)

## 15. Bildirim (`/api/notifications`)
- `GET /api/notifications` (kendine gelenler, sayfalı) → liste öğesi `{ id, recipientUserId, channel,
  title, body, read, createdAt }`
- `POST /api/notifications/{id}/read` — idempotent
- ADMIN: `POST /api/notifications/broadcast` (hedef: `targetRole` veya `studentIds`/`parentIds`) — IN_APP +
  e-posta (Mailpit, dev; `@Async` gönderim) → `{ recipientCount }` (benzersiz alıcı sayısı)

## 16. Konum takibi (`/api/tracking`)
- `POST /api/tracking/trips/{tripId}/locations` `{ lat, lng, recordedAt }` — sadece o sefere atanmış DRIVER
  → `{ id, lat, lng, recordedAt }`
- `GET /api/tracking/trips/{tripId}/latest` — rol bazlı kapsam (ADMIN tümü; DRIVER/HOSTESS kendi;
  PARENT/STUDENT ilişkili aktif sefer) → `{ lat, lng, recordedAt, staleSeconds, isOffline }`
  (`isOffline = staleSeconds > 90`)

## 17. Kurum ayarları (`/api/settings`) — ADMIN only
- `GET/PATCH /api/settings` → `{ id, name, address, phone, reportHeaderNote, defaultMonthlyFee }` (tek
  satır, tenant değil; `PATCH` yarı-kısmi: alan gönderilmezse `null`=değişmez, boş string `""`=temizle).
- `defaultMonthlyFee`: §19'daki veli öz-kayıt akışında yeni sözleşmelerin başlangıç ücreti olarak
  kullanılır. `null` iken self-enrollment `409` döner.

## 18. Kayıt talepleri (`/api/registration-requests`) — sadece ADMIN

Public `POST /api/auth/register-requests`in açtığı talepleri inceleme/onaylama/reddetme — bkz.
docs/SECURITY.md "Kayıt talebi ve ADMIN onayı" (tam davranış kuralları orada).

- `GET /api/registration-requests?status=&page=&size=&sort=` → sayfalı liste. `status` filtresi
  `PENDING_EMAIL_VERIFICATION`/`EMAIL_VERIFIED`/`APPROVED`/`REJECTED`; verilmezse tümü. Liste öğesi:
  `{ id, firstName, lastName, email, status, emailVerifiedAt, approvedAt, rejectedAt, rejectionReason,
  createdAt }` (asla `passwordHash` içermez).
- `GET /api/registration-requests/{id}` → aynı şekil, tekil.
- `POST /api/registration-requests/{id}/approve` `{ role, phone?, licenseNo? }` → SADECE
  `status=EMAIL_VERIFIED` iken izinli (aksi halde `409`). Yeni `app_user` oluşturur (şifre talepten
  kopyalanır), döner: `{ user: { id, fullName, email, phone, role, isActive } }` (mevcut
  `UserResponse` şekli). Role'e özgü domain profili AYRICA oluşturulur (hiçbir satır yazılmadan ÖNCE
  doğrulanır):
  - `role=PARENT` → bağlı bir `parent` satırı (`fullName`=kullanıcı adı, `phone`=bu isteğin `phone`
    alanı, `tcNo=null`) — 2026-10-25'te düzeltildi.
  - `role=HOSTESS` → bağlı bir `hostess` satırı (ek zorunlu alanı yok) — 2026-11-04'te düzeltildi.
  - `role=DRIVER` → `licenseNo` ZORUNLUDUR (`driver.license_no` NOT NULL); boş/eksikse hiçbir satır
    yazılmadan `400` döner. Verilirse bağlı bir `driver` satırı oluşturulur — 2026-11-04'te
    düzeltildi, bkz. değişiklik günlüğü ve docs/KNOWN_LIMITATIONS.md.
  - `role=ADMIN`/`role=STUDENT` → ayrı bir domain profil tablosu yok, sadece `app_user` oluşur.
- `POST /api/registration-requests/{id}/reject` `{ reason? }` → `status` `PENDING_EMAIL_VERIFICATION`
  veya `EMAIL_VERIFIED` iken izinli, `app_user` oluşturmaz.

## 19. Veli öz-kayıt (öğrenci kaydı sihirbazı)

2026-10-25: Yeni onaylanan bir PARENT hesabının, hiç bağlı öğrencisi yoksa, kendi başına İLK
öğrencisini kaydetmesini sağlar (frontend'de zorunlu bir sihirbaz olarak sunulur — bkz.
docs/KNOWN_LIMITATIONS.md). "Kayıt talebi" (§18) akışından farklı olarak ayrı bir "talep" tablosu
YOKTUR: gönderildiğinde gerçek `student`/`contract`/`parent_student_link` kayıtları **anında**
oluşur, ama `contract` `PENDING_APPROVAL` durumuyla başlar ve ADMIN onayına kadar `student`/
`contract` tam olarak "aktif" sayılmaz (frontend bu durumu `GET /api/students` + `GET /api/contracts`
ile kendi türetir, ayrı bir "durum" endpoint'i yoktur).

- `POST /api/parents/me/student-enrollments` (PARENT only) —
  `{ schoolId, relationType, parentPhone, student: { fullName, birthDate, tcNo, grade, gender,
  healthNote? }, address: { city, district, neighborhood, addressLine }, contractAccepted }`.
  `schoolId` zorunlu — §20'deki `school` listesinden seçilir (ADMIN'in tanımladığı bir okul
  DEĞİLSE/aktif değilse `400`); `student.schoolId` + `student.schoolName` (geriye dönük uyum için
  senkron) buradan set edilir.
  `parentId` request body'de YOKTUR — her zaman `@AuthenticationPrincipal`'dan çözülen veliye bağlanır
  (başka bir veliye öğrenci eklenemez). `parentPhone` zorunlu — kayıt talebi formunda (§18) telefon
  toplanmadığı için veli hesabının `phone` alanı genelde boştur; bu akış `parent.phone`'u BU DEĞERLE
  günceller (yan etki, ayrı bir `PATCH /api/parents/me` endpoint'i YOK — zaten doldurulmuşsa üzerine
  yazar). Kurallar:
  - `contractAccepted != true` → `400`.
  - Bu veliye ait zaten `PENDING_APPROVAL` durumda bir başvurusu varsa → `409`. **2026-11-18:** ilk öz-kayıt
    onaylandıktan (`ACTIVE`) sonra veli aynı hesabından ikinci/sonraki çocuğu için başvurabilir (önceden
    `ACTIVE` öğrencisi olan veliye de `409` dönüyordu). Aynı velinin eşzamanlı başvuruları veli satırı
    kilidiyle (`FOR UPDATE`) serileştirilir; onay yine aşağıdaki `approve` ile yapılır ve yeni bir bağ/
    sözleşme ÜRETMEZ (sadece başvuruda oluşan `PENDING_APPROVAL` sözleşme `ACTIVE` olur).
  - `tcNo` başka bir öğrencide kullanılıyorsa → `409` (mevcut `/api/students` ile aynı kural).
  - `institution_settings.defaultMonthlyFee == null` → `409`.
  - Başarılıysa tek transaction'da `Student` (`isActive=true`), `ParentStudentLink` (`isActive=true`,
    `relationType`), `Contract` (`monthlyFee=defaultMonthlyFee`, `startDate=bugün`,
    `status=PENDING_APPROVAL`) oluşturur → `201` `{ student: StudentResponse, contract:
    ContractResponse }`.
- `POST /api/contracts/{id}/approve` (ADMIN) `{ monthlyFee, startDate?, discountAmount? }` — SADECE
  `status=PENDING_APPROVAL` iken izinli (aksi halde `409`). `monthlyFee` zorunlu (ADMIN kurum
  varsayılanını görüp değiştirebilir); `startDate`/`discountAmount` verilmezse mevcut değer korunur.
  §11'deki ACTIVE-overlap kontrolü burada ÇALIŞIR (artık `ACTIVE`'e geçeceği için). Status → `ACTIVE`.
- `POST /api/contracts/{id}/reject` (ADMIN) `{ reason? }` — SADECE `status=PENDING_APPROVAL` iken
  izinli. 2026-11-18: `reason` `contract.rejection_reason`'a yazılır (`ContractResponse.rejectionReason`);
  onay/ret sonucu velilere IN_APP bildirim olarak gönderilir. `contract.status=CANCELLED`, bağlı `student.isActive=false`, bağlı `parent_student_link`
  revoke edilir (`isActive=false`, `revokedAt`) — hiçbir satır silinmez.

## 20. Okul (`/api/schools`)

2026-10-25: Taşıma firmasının servis verdiği okulların ADMIN tarafından yönetilen basit bir referans
listesi — **multi-tenant/firma seçimi DEĞİLDİR** (CLAUDE.md "tek firma" kuralı değişmedi, veri
izolasyonu yok, her ADMIN/personel tüm okullardaki öğrencileri aynı şekilde görür). §19'daki veli
öz-kayıt sihirbazında "okul seç" adımını beslemek için eklendi (Vehicle modülüyle AYNI basit CRUD
deseni).

- `GET /api/schools?q=&isActive=&page=&size=&sort=` (ADMIN: tümü; **PARENT: sadece aktif okullar,
  salt-okunur** — sihirbazdaki seçim listesi için) → sayfalı liste.
- `GET /api/schools/{id}` (ADMIN, PARENT salt-okunur) — detay.
- `POST /api/schools` (ADMIN) `{ name, address?, phone? }` — `name` benzersizdir (büyük/küçük harf
  duyarsız).
- `PATCH /api/schools/{id}` (ADMIN) `{ name?, address?, phone?, isActive? }` — Vehicle modülündeki
  gibi ayrı bir deactivate alt-endpoint'i YOK, `isActive` de bu endpoint üzerinden değiştirilir.
- `SchoolResponse`: `{ id, name, address, phone, isActive }`.

## 21. Planlı sefer düzenleme ve operasyon bildirimleri (2026-11-18)

- `PATCH /api/trips/{id}` (ADMIN) `{ plannedStartTime?, vehicleId?, driverId?, hostessId?, removeHostess? }`
  — `null` alan = değişmez; `removeHostess=true` hostesi kaldırır (`hostessId` ile birlikte `400`). Sadece
  `PLANNED` seferde (`409`). Güzergâh/tarih/yön değiştirilemez (yolcu listesini değiştirir — yeni sefer açılır).
  Araç/şoför/hostes aynı iş günü+yönde başka sefere atanmışsa `409`.
- **Kilit sırası ve transaction sınırı:** düzenleme/başlatma/tamamlama/iptal ve §23 iptal talebi onayı önce
  sefer satırını `SELECT ... FOR UPDATE` ile kilitler, durumu kilit altında okunan GÜNCEL değere göre doğrular,
  sonra çakışma sorgularını (SERIALIZABLE) ve yazmaları yapar. Eşzamanlı "başlat" + "düzenle"den ikincisi ya iş
  kuralı `409` ya da serileştirme `409 CONCURRENT_CONFLICT` alır; başlamış seferin ataması sessizce değişmez.
- **Bildirimler** (mevcut `/api/notifications`, kanal `IN_APP`): işlemle AYNI transaction'da yazılır (işlem
  rollback olursa bildirim de oluşmaz). Her olayın deterministik bir anahtarı vardır (`notification.event_key`,
  kısmi benzersiz indeks `(recipient_user_id, channel, event_key)` + `ON CONFLICT DO NOTHING`) — aynı olay
  yeniden işlendiğinde alıcıya ikinci kez gitmez.
  | Olay | Alıcı | Anahtar |
  |---|---|---|
  | Planlanan saat değişti | Yolcu listesindeki öğrenciler + aktif bağlı velileri | `trip:{id}:v{version}:planned-time` |
  | Personel ataması değişti | Çıkarılan ve eklenen şoför/hostes | `trip:{id}:v{version}:assigned:{userId}` / `:unassigned:{userId}` |
  | Araç değişti | Seferin şoför/hostesi | `trip:{id}:v{version}:vehicle` |
  | Sefer iptal edildi (doğrudan veya onaylanan talep) | Öğrenciler + veliler + personel | `trip:{id}:cancelled` |
  | Gecikme kaydı (§23) | Öğrenciler + veliler | `trip-incident:{id}` |
  | Arıza / yol kapanması / iptal talebi (§23) | Aktif ADMIN'ler | `trip-incident:{id}` |
  | Seferi etkileyen durak değişikliği (ADMIN atama güncellemesi `PATCH /api/assignments/{id}` veya onaylanan §25 talebi) | Öğrenci + veliler | `assignment:{id}:stop:{eski}-{yeni}:{validFrom}` / `stop-change-request:{id}:decided` |
  | Talep sonuçları (§23, §24, §25, §19) | Talep sahibi (+ §25'te öğrenci/veliler) | `...:{id}:resolved` / `:decided` / `contract:{id}:approved|rejected` |

## 22. Veli "servise binmeyecek" bildirimi (`/api/trips/{tripId}/absence-notices`)

Resmi yoklama (`attendance_event`) DEĞİLDİR ve onu değiştirmez; görevli öğrenci durağa gelirse binişi mevcut
kurallarla kaydeder.
- `POST` (PARENT) `{ studentId, note? }` → `201` (yeni) / `200` (zaten aktif olan bildirim döner). Kurallar:
  aktif veli–öğrenci bağı (yoksa `404`), öğrenci o seferin yolcu listesinde (`404`), sefer `PLANNED`/
  `IN_PROGRESS` (`409`), öğrenci için o seferde yoklama kaydı yok (`409`). Aynı öğrenci+sefer için tek AKTİF
  bildirim: öğrenci satırı kilidi + kısmi benzersiz indeks `uq_trip_absence_notice_active`.
- `POST /{noticeId}/withdraw` (PARENT, kendi çocuğu) → `200`; yoklama girildiyse veya sefer kapandıysa `409`.
- `GET` (ADMIN; atanmış DRIVER/HOSTESS, aksi `404`) → aktif bildirimler. Ayrıca roster satırında `absenceNotice`.
- `AbsenceNoticeResponse`: `{ id, tripId, studentId, studentFullName, parentFullName, note, status:
  ACTIVE|WITHDRAWN, createdAt, withdrawnAt }`.

## 23. Sefer operasyon kayıtları ve iptal talebi (`/api/trips/{tripId}/incidents`)

- `POST` (ADMIN; atanmış DRIVER/HOSTESS, aksi `404`) `{ type: DELAY|BREAKDOWN|ROAD_CLOSURE|CANCELLATION_REQUEST,
  delayMinutes?, note }` — `note` zorunlu (≤500), `DELAY` için `delayMinutes` 1..600 zorunlu (`400`). Sefer
  `PLANNED`/`IN_PROGRESS` değilse `409`. Bilgi kayıtları `RECORDED`; iptal talebi `OPEN` olarak açılır ve
  **seferin durumunu değiştirmez**. Açık bir iptal talebi varsa yenisi oluşmaz, mevcut talep `200` ile döner
  (`uq_trip_incident_open_cancellation`).
- `GET` (ADMIN; atanmış DRIVER/HOSTESS) → kayıtlar (yeni→eski).
- `POST /{incidentId}/resolve` (ADMIN) `{ approve, note }` — `note` zorunlu; sadece `OPEN` iptal talebi (aksi
  `409`, tekrar sonuçlandırma `409`). `approve=true` → sefer iptal edilir (`cancellationReason=note`, §21
  bildirimleri), `false` → `REJECTED`. Talep sahibine sonuç bildirilir.
- `TripIncidentResponse`: `{ id, tripId, type, status: RECORDED|OPEN|APPROVED|REJECTED, delayMinutes, note,
  reportedByFullName, reportedByRole, createdAt, resolutionNote, resolvedByFullName, resolvedAt }`.

## 24. Yoklama düzeltme / inceleme talepleri (`/api/attendance-corrections`)

- `POST` (DRIVER/HOSTESS kendi atandığı sefer; PARENT aktif bağlı çocuğu ve çocuğun yolcusu olduğu sefer;
  STUDENT/ADMIN `403`) `{ tripId, studentId, requestedStatus?, reason }`. `requestedStatus` ∈
  `NONE|BOARDED|DROPPED_OFF|ABSENT`; DRIVER/HOSTESS (`kind=CORRECTION`) için zorunlu, PARENT (`kind=REVIEW`)
  için opsiyonel. Öğrencinin o seferde mevcut bir kaydı olmalı (`409`); aynı sefer+öğrenci için bekleyen talep
  varsa `409` (`uq_acr_pending`). Talep anındaki durum `sourceStatus` olarak saklanır.
- `GET ?status=&tripId=&page=&size=&sort=` — ADMIN tümü; diğer roller SADECE kendi açtıkları. `GET /{id}` aynı
  kapsam (aksi `404`).
- `POST /{id}/approve` (ADMIN) `{ newStatus?, note? }` — `newStatus` yoksa `requestedStatus` (ikisi de yoksa
  `400`). Talep `PENDING` değilse `409` (talep satırı `FOR UPDATE` — eşzamanlı iki onaydan yalnız biri uygulanır).
  **Eski veri koruması:** güncel durum `sourceStatus`'tan farklıysa `409` — talep verisiyle üzerine yazılmaz
  (yanıtlarda `currentStatus` canlı hesaplanır, `stale=true`). Uygulama: hedef durum geçerli olay kümesine
  çevrilir (`BOARDED`=biniş, `DROPPED_OFF`=biniş+iniş, `ABSENT`=devamsızlık, `NONE`=kayıt yok); fazlalık kayıtlar
  SİLİNMEZ, `voided_at`/`voided_by_correction_id` ile geçersiz işaretlenir (tüm okumalardan düşer), eksikler
  `created_by_correction_id` ile eklenir — biniş/iniş sırası, tekrar kayıt ve devamsızlık çakışması kuralları
  korunur. `audit_log`'a `ATTENDANCE_CORRECTION_APPROVED` (eski/yeni durum, gerekçe, talep eden, karar veren,
  zaman, geçersizlenen/eklenen kayıtlar) yazılır; talep sahibine bildirim.
- `POST /{id}/reject` (ADMIN) `{ reason }` (zorunlu) — `PENDING` değilse `409`; `audit_log`
  `ATTENDANCE_CORRECTION_REJECTED`; talep sahibine bildirim.
- `AttendanceCorrectionResponse`: `{ id, tripId, tripBusinessDate, tripDirection, studentId, studentFullName,
  kind, sourceStatus, requestedStatus, currentStatus, stale, reason, status: PENDING|APPROVED|REJECTED,
  requestedByFullName, requestedByRole, createdAt, decidedByFullName, decidedAt, decisionNote, appliedStatus }`.

## 25. Durak değişikliği talepleri (`/api/stop-change-requests`)

- `GET /options?studentId=&direction=` (ADMIN; PARENT bağlı çocuk, aksi `404`) → o yöndeki AKTİF güzergâhların
  durakları `[{ stopId, stopName, plannedTime, sequenceNo, routeId, routeName }]`.
- `POST` (PARENT) `{ studentId, direction, requestedStopId, effectiveDate, reason? }` — durak aktif güzergâhta ve
  yön uyumlu (`400`), `effectiveDate` ≥ bugün (Europe/Istanbul, `400`), aynı öğrenci+yön için bekleyen talep
  varsa `409` (`uq_scr_pending`). ADMIN'lere bildirim.
- `GET ?status=&page=&size=&sort=` — ADMIN tümü; PARENT kendi açtıkları.
- `POST /{id}/approve` (ADMIN) `{ note? }` — SERIALIZABLE + talep satırı kilidi; tekrar sonuçlandırma `409`.
  Onay anında yeniden doğrulanır: durak aktif/yön uyumlu, yürürlük ≥ bugün, öğrencinin çakışan başka ataması yok
  (aksi `409`). Mevcut atama yürürlük−1 günde kapatılır ve yürürlükten itibaren (eski bitişe kadar) yeni atama
  açılır; mevcut atama aynı gün başlıyorsa sadece güzergâh/durak güncellenir. `audit_log`
  `STOP_CHANGE_APPROVED`. Öğrenci + velilere bildirim.
- `POST /{id}/reject` (ADMIN) `{ reason }` (zorunlu, `400`) — velilere bildirim.
- `StopChangeRequestResponse`: `{ id, studentId, studentFullName, direction, currentStopName, requestedStopId,
  requestedStopName, requestedRouteName, effectiveDate, reason, status, decisionNote, decidedAt, createdAt,
  requestedByFullName, resultingAssignmentId }`.

## 26. Öğrencinin günlük servisi (`GET /api/students/{id}/daily-service?date=`)

ADMIN; PARENT (aktif bağlı çocuk); STUDENT (kendisi); diğerleri `403`, kapsam dışı `404`. `date` yoksa bugün
(Europe/Istanbul). → `[{ tripId, direction, businessDate, status, plannedStartTime, routeName, vehiclePlate,
stopId, stopName, stopPlannedTime, attendanceStatus, absenceNotice, canSubmitAbsenceNotice,
canWithdrawAbsenceNotice, latestIncident: { type, delayMinutes, note, createdAt } | null, cancellationReason }]`
— o gün öğrencinin aktif atamasının güzergâhındaki gidiş/dönüş seferleri (sefer yoksa boş dizi).
`canSubmit…/canWithdraw…` sadece PARENT için `true` olabilir.

## 27. Finans belgeleri (makbuz / ekstre)

- `GET /api/payments/{id}/receipt` ve `GET /api/students/{id}/statement/document` — ADMIN; PARENT sadece aktif
  bağlı çocuğu (aksi `404`); STUDENT/DRIVER/HOSTESS `403`. Yanıt `text/html; charset=UTF-8`,
  `Content-Disposition: attachment; filename="makbuz-{id}.html"` / `"ekstre-{studentId}.html"`. Projede mevcut
  PDF üretimi olmadığı için belge tarayıcıda açılıp yazdırılabilen/PDF'e kaydedilebilen bağımsız bir HTML
  dosyasıdır; veriler mevcut ödeme/ekstre servislerinden gelir. Biçim: `1.250,00 TL` (TRY, tr-TR), tarih
  `dd.MM.yyyy HH:mm` (Europe/Istanbul). BFF passthrough `content-disposition` başlığını iletir.


---

## Sözleşme değişiklik günlüğü
| Tarih | Değişiklik | Gerekçe |
|---|---|---|
| 2026-10-15 | İlk sürüm oluşturuldu (Faz B) | Proje başlangıcı |
| 2026-10-15 | `/refresh` ve `/logout` request/response body'leri netleştirildi (`{refreshToken}`) | Backend Agent'ın uyguladığı gerçek sözleşme + Frontend Agent'ın BFF tarafında yaptığı varsayımla eşleşti, dokümana işlendi (Faz C/D) |
| 2026-10-16 | `GET /api/parent-student-links?studentId=&parentId=` eklendi | Frontend Agent Faz E'de veli-öğrenci bağlantılarını listeleyecek bir endpoint olmadığını buldu (sadece create/revoke tanımlıydı), oturum-içi geçici liste ile idare etmişti — koordinatör eksikliği giderdi, Backend Agent bir sonraki turda uygulayacak |
| 2026-10-21 | `GET /api/trips/{id}/roster` eklendi | Faz F'den kalma bilinen kapsam boşluğu: DRIVER/HOSTESS bir seferin tam yolcu listesini göremiyordu (manuel öğrenci ID girişine düşüyordu). Koordinatör onayıyla (kullanıcı talebi) eklendi — RBAC_MATRIX.md da güncellendi |
| 2026-10-20 | §11/§13'e zaten KODDA var olan ama dokümante edilmemiş `GET /api/contracts`, `GET /api/contracts/{id}`, `PATCH /api/contracts/{id}`, `POST /api/contracts/{id}/cancel`, `GET /api/payments`, `GET /api/payments/{id}` eklendi; §14-17'ye gerçek DTO alan adlarıyla response örnekleri eklendi | Faz I docs↔kod tutarlılık taraması: davranış değişmedi (bu endpoint'ler zaten test edilmiş, çalışır haldeydi), sadece dokümantasyon gerçeğe uyduruldu. Response örnekleri, Faz H'de yaşanan alan-adı-varsayımı hatasının (completedTrips/paymentCount/totalOutstanding vb. YANLIŞ varsayılmıştı) tekrarını önlemek için eklendi |
| 2026-10-23 | `/login`e `rememberMe` request alanı + `refreshTokenExpiresAt` response alanı eklendi; `register-requests`/`verify-email`/`social-login`/`social-link`/`social/**` (§1) ve yeni §18 `/api/registration-requests` (ADMIN onay akışı) eklendi | Kullanıcı talebi: profesyonel login/register deneyimi — "beni hatırla" + kayıt talebi/ADMIN onayı + sosyal giriş mimarisi. RBAC_MATRIX.md kural 1 SINIRLI güncellendi (bkz. docs/KNOWN_LIMITATIONS.md Karar Günlüğü) |
| 2026-10-24 | `/social-login`e zorunlu `X-Internal-Service-Key` header'ı eklendi (yoksa/yanlışsa `401`) | Güvenlik taraması: bu uç nokta `permitAll` olduğu için "BFF dışından çağrılamaz" varsayımı KOD TARAFINDAN ZORLANMIYORDU — herkes doğrudan çağırıp `emailVerified=true` göndererek e-posta doğrulamasını atlayabilir veya zaten bağlı bir kimliğin provider+providerUserId çiftini tahmin ederek o kullanıcı olarak giriş yapabilirdi. Davranış sözleşmesi (response şekilleri) DEĞİŞMEDİ, sadece BFF-dışı çağıranlar artık 401 alır |
| 2026-10-25 | §11'e `contract.status=PENDING_APPROVAL` + `POST /api/contracts/{id}/approve`/`/reject` eklendi; §17'ye `defaultMonthlyFee` eklendi; yeni §19 `POST /api/parents/me/student-enrollments` eklendi | Kullanıcı talebi: yeni onaylanan bir PARENT'ın dashboard'a girmeden önce ZORUNLU olarak ilk öğrencisini kendi kaydetmesi (Yerlem benzeri sihirbaz, okul-seçim adımı HARİÇ — tek firma). Hibrit model: ayrı "talep" tablosu yok, gerçek `student`/`contract` anında oluşur ama `PENDING_APPROVAL` durumuyla ADMIN onayı bekler |
| 2026-10-25 | §18 `POST /api/registration-requests/{id}/approve`: `role=PARENT` iken artık bağlı bir `parent` satırı da oluşturuluyor | Koordinatör doğrulaması sırasında (veli öz-kayıt sihirbazının gerçek bir yeni veli hesabıyla uçtan uca testinde) bulunan GERÇEK bir regresyon: kayıt talebi akışıyla onaylanan HİÇBİR veli daha önce bir `parent` satırına sahip olmuyordu (eski ADMIN iki-adımlı akışının — `POST /api/users` + `POST /api/parents` — ikinci adımı bu akışta hiç yapılmıyordu), bu yüzden `ParentService.getByUserId` her zaman 404 atıyordu — önceki hiçbir test/akış bu satırı gerektirmediği için fark edilmemişti |
| 2026-10-24 | `GET /api/routes/{id}/stops` ve `GET /api/routes/{id}/stops/{stopId}` DRIVER/HOSTESS'e açıldı (kendi atanmış route'uyla aynı scope, kapsam dışı 404); create/update/delete ADMIN-only kaldı | Kullanıcı talebi: önceden "henüz karara bağlanmamış" olarak işaretli ürün sorusu karara bağlandı — ayrıca mevcut bir frontend bug'ını (DRIVER/HOSTESS `/guzergahlar/{id}` sayfasında "Duraklar" paneli 403 ile hata veriyordu) çözdü |
| 2026-10-25 | Yeni §20 `/api/schools` (ADMIN CRUD + PARENT salt-okunur liste) eklendi; §19'daki enrollment isteğine `schoolId` zorunlu alanı, §3'e `StudentResponse.schoolId` eklendi | Kullanıcı talebi: veli öz-kayıt sihirbazına Yerlem benzeri bir "okul seç" adımı — ADMIN'in yönettiği basit bir referans listesinden (multi-tenant DEĞİL, CLAUDE.md "tek firma" kuralı korunuyor) |
| 2026-11-04 | §3: `StudentCreateRequest`/`StudentUpdateRequest`'teki serbest metin `schoolName` alanı `schoolId`'ye ÇEVRİLDİ (geçersiz id `400`; backend seçilen okulun adını `schoolName`'e senkron yazar) | Kullanıcı talebi: ADMIN'in doğrudan öğrenci oluşturma formu da §20'deki okul listesinden dropdown ile seçim yapsın (önceden bilinen sınırlama — bkz. docs/KNOWN_LIMITATIONS.md 2026-10-25 maddesi, bu turda ÇÖZÜLDÜ) |
| 2026-11-04 | §18 `POST /api/registration-requests/{id}/approve`'a opsiyonel `licenseNo` alanı eklendi; `role=HOSTESS` ve `role=DRIVER` (licenseNo verilirse) için de artık bağlı domain satırı (`hostess`/`driver`) oluşturuluyor; `role=DRIVER` + `licenseNo` boşsa `400` | Kullanıcı kararı (AskUserQuestion): bilinen sınırlama (kayıt talebiyle onaylanan DRIVER/HOSTESS için domain satırı oluşmaması, bkz. docs/KNOWN_LIMITATIONS.md 2026-10-25 maddesi) giderildi. HOSTESS'in PARENT dışında hiç ek zorunlu alanı olmadığı için otomatik oluşturma kullanıcı kararı gerektirmedi; DRIVER için licenseNo'nun zorunlu mu opsiyonel mi olacağı kullanıcıya soruldu — ZORUNLU (400) seçildi, Driver satırı hep eksiksiz oluşsun diye |
| 2026-11-18 | §9 `TripResponse` genişletildi, `plannedStartTime`, hostes çakışma kontrolü, `PATCH /api/trips/{id}`; `POST /api/trips/{id}/cancel` SADECE ADMIN; roster'a `stopPlannedTime`/`absenceNotice`/`pendingCorrectionId`; §19 ikinci çocuk başvurusu + `rejectionReason`; yeni §21–§27 (bildirim olayları, veli "servise binmeyecek" bildirimi, operasyon kayıtları/iptal talebi, yoklama düzeltme, durak değişikliği, günlük servis, makbuz/ekstre) | Kullanıcı talebi: rol bazlı günlük operasyon geliştirmeleri (V14 migration) |
| 2026-11-24 | §3 "Öğrenci hesabı bağı": `PUT/DELETE /api/students/{id}/account` (ADMIN); `GET /api/users?studentLinked=`; bağlı STUDENT hesabının rol değişikliği `409` | Sprint 3 kullanıcı talebi: STUDENT kullanıcısını öğrenci kaydına bağlayan API yoktu (Sprint 2 açık işi), seed bağı DB'den kuruyordu. Mevcut `student.user_id` + tekil indeks kullanıldı, migration gerekmedi |
