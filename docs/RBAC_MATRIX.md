# Yetki (RBAC) Matrisi

> Bu uygulama **tek bir okul servis firması** içindir. Multi-tenant / firma seçimi yoktur.
> Kullanıcı girişi talep üzerine **5 role** çıkarılmıştır: `ADMIN`, `DRIVER`, `HOSTESS`, `PARENT`, `STUDENT`.
> `STUDENT` rolü, kullanıcıyla netleştirme sonucu eklenmiştir (bkz. docs/KNOWN_LIMITATIONS.md "Karar Günlüğü").
> Roller sabittir; yeni rol türetme veya firma/tenant bazlı rol icat etme yoktur.

## Rol özetleri

| Rol | Kapsam |
|---|---|
| ADMIN | Kurumun tüm teknik ve operasyonel yönetimi: kullanıcı, öğrenci, veli, araç, personel, güzergâh, sözleşme, ücret, ödeme, rapor, ayarlar. |
| DRIVER | Yalnızca **kendisine atanmış** aktif sefer/araç/öğrencilere erişir. Konum gönderir, biniş/iniş/devamsızlık işler. |
| HOSTESS | Yalnızca **kendisine atanmış** aktif sefer/öğrencilere erişir. Yoklama işler. |
| PARENT | Yalnızca **kendisine bağlı (aktif ilişkili)** öğrencilerin izin verilen verilerine erişir (yoklama, sözleşme özeti, borç, ödeme, ekstre, izinli konum). |
| STUDENT | Yalnızca **kendi** kaydına ait salt-okunur veriler: kendi yoklama geçmişi, kendi güzergâh/sefer bilgisi, kendi bildirimleri, izinli konum. Finans ve kullanıcı yönetimine erişemez. |

## Nesne bazlı erişim ilkesi

Her ID ile erişilen kaynakta (öğrenci, veli, sefer, ödeme, konum, sözleşme...) sunucu tarafında **sahiplik/ilişki kontrolü** yapılır:
- DRIVER/HOSTESS → sadece kendi aktif atamalarındaki sefer/öğrenci ID'leri.
- PARENT → sadece `parent_student` tablosunda aktif ilişkisi olan `student_id`'ler.
- STUDENT → sadece kendi `student_id`'si.

Frontend'de buton/menü gizleme UX amaçlıdır, **yetkilendirme sayılmaz**. Backend her zaman bağımsız doğrular.

## Endpoint x Rol matrisi (özet)

Durum kodları: ✅ izinli (tam) · 🟡 izinli (sadece kendi/atanmış kapsam) · ❌ yasak

| Modül / Endpoint grubu | ADMIN | DRIVER | HOSTESS | PARENT | STUDENT |
|---|---|---|---|---|---|
| POST /api/auth/login, /logout, /refresh | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/auth/me | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/auth/change-password | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/auth/forgot-password, /reset-password | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/auth/register-requests, /register-requests/verify-email | public (kimliksiz) | | | | |
| POST /api/auth/social-login | public (BFF→backend, server-to-server) | | | | |
| POST /api/auth/social-link | ✅ (kendi hesabına, authenticated) | ✅ | ✅ | ✅ | ✅ |
| /api/registration-requests/** (listele/onayla/reddet) | ✅ | ❌ | ❌ | ❌ | ❌ |
| /api/users/** (kullanıcı CRUD, rol atama, aktif/pasif) | ✅ | ❌ | ❌ | ❌ | ❌ |
| /api/students/** (liste, CRUD) | ✅ | ❌ | ❌ | 🟡 (sadece kendi çocukları, salt okunur) | 🟡 (sadece kendisi, salt okunur) |
| PUT/DELETE /api/students/{id}/account (STUDENT hesabı bağla / bağı kaldır, Sprint 3 — 2026-11-24) | ✅ | ❌ | ❌ | ❌ | ❌ |
| /api/parents/** (CRUD, öğrenci bağlama) | ✅ | ❌ | ❌ | 🟡 (kendi profili, salt okunur) | ❌ |
| /api/vehicles/** | ✅ | 🟡 (kendi atanmış aracı, salt okunur) | ❌ | ❌ | ❌ |
| /api/drivers/**, /api/hostesses/** (CRUD) | ✅ | 🟡 (kendi profili, salt okunur) | 🟡 (kendi profili, salt okunur) | ❌ | ❌ |
| /api/routes/** | ✅ | 🟡 (kendi atanmış güzergâh, salt okunur — `GET` liste/detay) | 🟡 (kendi atanmış güzergâh, salt okunur — `GET` liste/detay) | ❌ | ❌ |
| /api/routes/{id}/stops/** (durak CRUD) | ✅ | ❌ | ❌ | ❌ | ❌ |
| GET /api/routes/{id}/stops, GET /api/routes/{id}/stops/{stopId} (durak liste/detay) | ✅ | 🟡 (kendi atanmış güzergâhın durakları, salt okunur) | 🟡 (kendi atanmış güzergâhın durakları, salt okunur) | ❌ | ❌ |
| /api/assignments/** (öğrenci-güzergâh/durak) | ✅ | ❌ | ❌ | 🟡 (kendi çocuklarının ataması, salt okunur) | 🟡 (kendi ataması, salt okunur) |
| /api/trips/** (sefer CRUD/yönetim) | ✅ | 🟡 (kendi atanmış sefer) | 🟡 (kendi atanmış sefer) | 🟡 (çocuğunun seferi, salt okunur) | 🟡 (kendi seferi, salt okunur) |
| POST /api/trips/{id}/start, /complete | ✅ | 🟡 (kendi atanmış sefer) | 🟡 (kendi atanmış sefer) | ❌ | ❌ |
| POST /api/trips/{id}/cancel (doğrudan iptal, 2026-11-18) | ✅ | ❌ (iptal TALEBİ açar) | ❌ (iptal TALEBİ açar) | ❌ | ❌ |
| PATCH /api/trips/{id} (planlı sefer düzenleme, 2026-11-18) | ✅ | ❌ | ❌ | ❌ | ❌ |
| GET /api/trips/{id}/roster (yolcu listesi + yoklama durumu + veli bildirimi) | ✅ | 🟡 (kendi atanmış sefer) | 🟡 (kendi atanmış sefer) | ❌ | ❌ |
| POST/GET /api/trips/{id}/incidents (gecikme/arıza/yol kapanması/iptal talebi) | ✅ | 🟡 (kendi atanmış sefer) | 🟡 (kendi atanmış sefer) | ❌ | ❌ |
| POST /api/trips/{id}/incidents/{incidentId}/resolve (iptal talebini sonuçlandır) | ✅ | ❌ | ❌ | ❌ | ❌ |
| POST /api/trips/{id}/absence-notices, /{noticeId}/withdraw ("servise binmeyecek") | ❌ | ❌ | ❌ | 🟡 (aktif bağlı çocuk + çocuğun yolcusu olduğu açık sefer) | ❌ |
| GET /api/trips/{id}/absence-notices | ✅ | 🟡 (kendi atanmış sefer) | 🟡 (kendi atanmış sefer) | ❌ | ❌ |
| GET /api/students/{id}/daily-service ("Bugünkü Servisim") | ✅ | ❌ | ❌ | 🟡 (kendi çocuğu) | 🟡 (kendisi) |
| POST /api/attendance-corrections (düzeltme / inceleme talebi) | ❌ | 🟡 (kendi atanmış seferindeki kayıt) | 🟡 (kendi atanmış seferindeki kayıt) | 🟡 (kendi çocuğunun kaydı) | ❌ |
| GET /api/attendance-corrections[/{id}] | ✅ | 🟡 (kendi açtığı) | 🟡 (kendi açtığı) | 🟡 (kendi açtığı) | ❌ |
| POST /api/attendance-corrections/{id}/approve, /reject | ✅ | ❌ | ❌ | ❌ | ❌ |
| GET /api/stop-change-requests/options, GET /api/stop-change-requests | ✅ | ❌ | ❌ | 🟡 (kendi çocuğu / kendi açtığı) | ❌ |
| POST /api/stop-change-requests | ❌ | ❌ | ❌ | 🟡 (kendi çocuğu) | ❌ |
| POST /api/stop-change-requests/{id}/approve, /reject | ✅ | ❌ | ❌ | ❌ | ❌ |
| POST /api/trips/{tripId}/attendance-events/boardings, /dropoffs | ✅ | 🟡 (kendi atanmış sefer) | 🟡 (kendi atanmış sefer) | ❌ | ❌ |
| POST /api/trips/{tripId}/attendance-events/absences | ✅ | 🟡 (kendi atanmış sefer) | 🟡 (kendi atanmış sefer) | ❌ | ❌ |
| GET /api/students/{id}/attendance | ✅ | 🟡 (kendi seferindeki kayıtlar) | 🟡 (kendi seferindeki kayıtlar) | 🟡 (kendi çocuğu) | 🟡 (kendisi) |
| /api/contracts/** | ✅ | ❌ | ❌ | 🟡 (kendi çocuğu, salt okunur özet) | ❌ |
| POST /api/contracts/{id}/approve, /reject (öz-kayıt onayı, bkz. API.md §19) | ✅ | ❌ | ❌ | ❌ | ❌ |
| POST /api/parents/me/student-enrollments (veli öz-kayıt / ikinci çocuk, bkz. API.md §19) | ❌ | ❌ | ❌ | 🟡 (sadece kendi; bekleyen başvurusu yoksa — 2026-11-18'den itibaren ikinci/sonraki çocuk dahil) | ❌ |
| /api/schools/** (bkz. API.md §20) | ✅ | ❌ | ❌ | 🟡 (sadece GET, sadece aktif okullar — öz-kayıt sihirbazı seçim listesi) | ❌ |
| /api/billing/** (aylık ücret/tahakkuk) | ✅ | ❌ | ❌ | 🟡 (kendi çocuğu, salt okunur) | ❌ |
| /api/payments/** | ✅ | ❌ | ❌ | 🟡 (kendi çocuğu, salt okunur + kendi ödeme geçmişi) | ❌ |
| /api/students/{id}/statement (ekstre) | ✅ | ❌ | ❌ | 🟡 (kendi çocuğu) | ❌ |
| GET /api/payments/{id}/receipt, GET /api/students/{id}/statement/document (makbuz/ekstre indirme) | ✅ | ❌ | ❌ | 🟡 (kendi çocuğu) | ❌ |
| /api/notifications/** | ✅ (gönderim+liste) | 🟡 (kendine gelen) | 🟡 (kendine gelen) | 🟡 (kendine gelen) | 🟡 (kendine gelen) |
| /api/tracking/** (konum gönder/oku) | ✅ (salt izleme) | 🟡 (kendi atanmış aktif sefer - gönder+oku) | 🟡 (kendi atanmış aktif sefer - sadece oku) | 🟡 (çocuğunun aktif seferi - sadece oku) | 🟡 (kendi aktif seferi - sadece oku) |
| /api/reports/** (operasyon/finans raporları) | ✅ | ❌ | ❌ | ❌ | ❌ |
| /api/settings/** (kurum ayarları) | ✅ | ❌ | ❌ | ❌ | ❌ |

## Kritik kurallar

1. **Public register, DOĞRUDAN aktif kullanıcı oluşturmaz** (2026-10-22'de kullanıcı talebiyle sınırlı
   olarak güncellendi — bkz. docs/KNOWN_LIMITATIONS.md Karar Günlüğü). `POST /api/auth/register-requests`
   herkese açıktır ama sadece bir `registration_request` kaydı oluşturur; bu kayıt e-posta
   doğrulaması + **ADMIN onayı** (`POST /api/registration-requests/{id}/approve`) olmadan HİÇBİR
   `app_user` satırına dönüşmez ve hiçbir korunan API'ye erişemez. Rolü (PARENT/STUDENT/DRIVER/
   HOSTESS/ADMIN) her zaman ADMIN belirler — public formda `role` alanı YOKTUR, istemciden gelen
   olası `role`/`isActive`/`approved` alanları DTO'da tanımlı olmadığı için sunucu tarafından
   zaten yok sayılır. `/api/users` üzerinden ADMIN'in doğrudan kullanıcı oluşturması (eski akış)
   DEĞİŞMEDİ, hâlâ kullanılabilir — registration-request bir EK, alternatif giriş kapısıdır.
2. **Son aktif ADMIN korumalıdır.** Sistemde en az bir aktif ADMIN kalmayacak şekilde pasife alma / rol düşürme işlemi `409 Conflict` ile reddedilir.
3. **Rol ataması** yalnızca ADMIN kullanıcı yönetimi endpoint'lerinde yapılır; başka hiçbir endpoint role/isActive alanlarını istemciden kabul etmez.
4. Rol veya parola değişikliğinde kullanıcının **tüm aktif oturumları/refresh token'ları geçersizleştirilir** (bkz. docs/SECURITY.md).
5. Pasif (isActive=false) kullanıcı login olamaz; mevcut access token süresi dolana kadar da reddedilir (refresh/me endpoint'lerinde isActive tekrar kontrol edilir).
6. **Veli öz-kayıt da ADMIN onayı olmadan "aktif" sayılmaz** (2026-10-25, bkz. docs/API.md §19).
   `POST /api/parents/me/student-enrollments` gerçek `student`/`contract` satırlarını ANINDA oluşturur
   (kural 1'deki registration-request'ten FARKLI olarak ayrı bir "talep" tablosu yoktur) ama
   `contract.status=PENDING_APPROVAL` ile başlar; ADMIN `approve`/`reject` etmeden sözleşme `ACTIVE`
   olmaz ve veli uygulamanın geri kalanına erişemez (frontend gate, bkz. docs/PROGRESS.md).
7. **Doğrudan sefer iptali SADECE ADMIN'dedir** (2026-11-18). DRIVER/HOSTESS backend'de `403` alır; BFF bu
   isteği aynen backend'e iletir (ayrı bir izin listesi yoktur) ve arayüz bu butonu göstermez. Personel
   `CANCELLATION_REQUEST` türünde operasyon kaydı (iptal talebi) açar; talep sefer durumunu değiştirmez,
   ADMIN gerekçesiyle onaylar/reddeder. Başlatma/tamamlama yetkileri değişmedi.
8. **Veli "servise binmeyecek" bildirimi resmi yoklama değildir** (2026-11-18): `attendance_event`'e yazmaz;
   görevli binişi kaydedebilir. Bildirim yoklama girilene veya sefer kapanana kadar geri alınabilir.
9. **Talep kapsamı** (2026-11-18): yoklama düzeltme / durak değişikliği taleplerinde ADMIN dışı roller sadece
   kendi açtıkları talepleri görür; sonuçlandırma yalnızca ADMIN'dedir ve her talep bir kez sonuçlanır.

10. **STUDENT hesabı ↔ öğrenci kaydı bağı** (Sprint 3, 2026-11-24; bkz. API.md §3 "Öğrenci hesabı bağı"):
    bağı yalnız ADMIN kurar/kaldırır. Bir hesap en fazla bir öğrenciye, bir öğrenci en fazla bir hesaba bağlanır;
    yalnız aktif STUDENT rolündeki hesap aktif öğrenci kaydına bağlanabilir. Var olan bağ sessizce değiştirilmez
    (farklı hesap/öğrenci `409`); önce kaldırılır. STUDENT'ın erişim kapsamı her istekte veritabanındaki bağdan
    türetilir: bağ kaldırıldığı anda mevcut access token da o kaydı göremez ve hesabın refresh token'ları iptal
    edilir. Bağlı STUDENT hesabının rolü bağ kaldırılmadan değiştirilemez (`409`); hesap pasife alınırsa bağ
    korunur ama hesap giriş yapamaz (kural 5), yeniden aktifleştirilince erişim geri gelir.