# Veritabanı Tasarımı (özet model — Flyway migration'ları bu modeli uygular)

Tüm tablolarda ortak audit alanları: `created_at timestamptz not null default now()`,
`updated_at timestamptz not null default now()` (trigger ile güncellenir), para alanlarında
`numeric(12,2)`. `ddl-auto` kullanılmaz; şema tamamen Flyway migration'ları ile kurulur, Hibernate sadece
`validate` modunda çalışır.

## Kullanıcı ve kimlik
- **app_user**(id, email UNIQUE(lower), password_hash, full_name, phone, role, is_active, created_at, updated_at)
  - CHECK role IN ('ADMIN','DRIVER','HOSTESS','PARENT','STUDENT')
- **refresh_token**(id, user_id FK, token_hash UNIQUE, expires_at, revoked_at NULL, created_at)
  - `expires_at`, login sırasındaki "Beni hatırla" tercihine göre DEĞİŞKEN TTL ile hesaplanır
    (bkz. docs/SECURITY.md "Beni hatırla") — satırda ayrı bir `remember_me` kolonu YOKTUR, tercih
    sadece `expires_at`'a yansır (token'ın kendisi "hatırlandı mı" bilgisini taşımaz, sadece ne
    zaman süreceğini).
- **password_reset_token**(id, user_id FK, token_hash UNIQUE, expires_at, used_at NULL, created_at)
  - partial index: `WHERE used_at IS NULL` (aktif token sorgusu için)
- **registration_request**(id, first_name, last_name, email, password_hash, status, email_verified_at NULL,
  approved_at NULL, approved_by_user_id FK NULL, rejected_at NULL, rejection_reason NULL,
  created_user_id FK NULL, created_at, updated_at)
  - CHECK status IN ('PENDING_EMAIL_VERIFICATION','EMAIL_VERIFIED','APPROVED','REJECTED')
  - `password_hash`: kullanıcının public formda kendi seçtiği şifrenin hash'i — ADMIN onayladığında
    AYNI hash yeni `app_user` satırına kopyalanır (ayrı bir geçici şifre/ikinci reset akışı YOK).
  - email UNIQUE DEĞİL (aynı e-posta ile zaman içinde birden fazla satır olabilir — örn. önce
    REJECTED, sonra yeniden başvurulmuş APPROVED; "aktif/tekrarsız talep" kuralı DB kısıtı değil,
    serviste email+status bazlı sorgu ile uygulanır, bkz. docs/SECURITY.md).
  - index: `lower(email)`, `status`.
- **registration_verification_token**(id, registration_request_id FK, token_hash UNIQUE, expires_at,
  used_at NULL, created_at, updated_at) — `password_reset_token` ile AYNI desen (SHA-256 hash, tek
  kullanımlık, süreli). TTL: 24 saat (parola sıfırlamadan daha uzun — hassasiyeti daha düşük bir
  "kimlik doğrulama" adımı, kullanıcıya e-postasını kontrol etmesi için daha fazla zaman tanınır).
  - partial index: `WHERE used_at IS NULL`.
- **oauth_identity**(id, provider, provider_user_id, email NULL, user_id FK NULL, registration_request_id
  FK NULL, created_at, updated_at)
  - CHECK provider IN ('GOOGLE','FACEBOOK','X')
  - UNIQUE(provider, provider_user_id)
  - CHECK: `user_id` ve `registration_request_id`'den TAM OLARAK BİRİ dolu olmalı (bir kimlik ya
    onaylanmış bir kullanıcıya ya da henüz onay bekleyen bir kayıt talebine bağlıdır, ikisine birden
    DEĞİL) — bkz. docs/SECURITY.md "Sosyal giriş".

## Okul
- **school**(id, name UNIQUE(case-insensitive) NOT NULL, address NULL, phone NULL, is_active NOT NULL
  default true) — 2026-10-25'te eklendi (V13 migration). ADMIN'in CRUD ile yönettiği basit bir
  referans listesi — **multi-tenant/firma DEĞİL** (CLAUDE.md'nin "tek firma" kuralı duruyor; bu
  tablo sadece taşıma firmasının servis verdiği okulları adlandırmak için var, her okul kendi
  verisine izole bir "tenant" değil — tüm ADMIN/personel tüm okullardaki öğrencileri aynı şekilde
  görür, veri ayrımı YOK).

## Öğrenci / Veli
- **student**(id, user_id FK NULL UNIQUE — STUDENT rolüyle girişi varsa bağlanır; Sprint 3 (2026-11-24) itibarıyla yalnız ADMIN `PUT/DELETE /api/students/{id}/account` ile kurulur/kaldırılır, 1-1 kuralı `uq_student_user_id` kısmi tekil indeksi + satır kilitleriyle korunur (API.md §3), full_name, birth_date,
  tc_no NULL UNIQUE(where not null), school_name, school_id FK NULL → school(id), grade, is_active,
  gender NULL CHECK IN ('MALE','FEMALE'), health_note NULL, city NULL, district NULL,
  neighborhood NULL, address_line NULL)
  - `gender`/`health_note`/`city`/`district`/`neighborhood`/`address_line`: 2026-10-25'te veli
    öz-kayıt (self-enrollment) sihirbazı için eklendi (V12 migration) — tamamı nullable, ADMIN'in
    doğrudan oluşturduğu mevcut/yeni öğrencileri etkilemez, sadece self-enrollment akışında zorunlu
    kılınır (uygulama seviyesinde, DB seviyesinde değil).
  - `school_id`: 2026-10-25'te eklendi (V13) — veli öz-kayıt sihirbazına Yerlem benzeri bir "okul
    seç" adımı eklendi (bkz. docs/API.md §19), seçilen `school`'un id'si buraya yazılır VE
    `school_name` (eski serbest metin alan) o okulun adıyla senkron tutulur (geriye dönük uyum,
    mevcut `school_name` okuyan hiçbir yer kırılmaz). ADMIN'in `/api/students` üzerinden DOĞRUDAN
    öğrenci oluşturma formu bu turda dropdown'a GEÇİRİLMEDİ — hâlâ serbest metin `school_name`
    kullanıyor, `school_id` o yoldan `null` kalır (bkz. docs/KNOWN_LIMITATIONS.md).
- **parent**(id, user_id FK UNIQUE, full_name, phone, tc_no NULL UNIQUE(where not null))
- **parent_student_link**(id, parent_id FK, student_id FK, relation_type, is_active, revoked_at NULL,
  created_at) — UNIQUE(parent_id, student_id)

## Araç / Personel
- **vehicle**(id, plate UNIQUE(normalized upper/no-space), capacity, model, is_active)
- **driver**(id, user_id FK UNIQUE, license_no, is_active)
- **hostess**(id, user_id FK UNIQUE, is_active)

## Güzergâh / Durak / Atama
- **route**(id, name, direction CHECK IN ('MORNING','AFTERNOON'), is_active)
- **stop**(id, route_id FK, name, sequence_no, planned_time) — UNIQUE(route_id, sequence_no)
- **student_route_assignment**(id, student_id FK, route_id FK, stop_id FK, direction, valid_from,
  valid_to NULL, created_at)
  - Çakışma kontrolü: aynı student_id+direction için `valid_from/valid_to` aralığı overlap eden ikinci
    aktif kayıt **uygulama seviyesinde** (serializable transaction + exclusion kontrolü) engellenir.

## Sefer / Yoklama
- **trip**(id, route_id FK, vehicle_id FK, driver_id FK, hostess_id FK NULL, business_date, direction,
  status CHECK IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED'), started_at NULL, completed_at NULL,
  version int — optimistic locking)
  - UNIQUE(vehicle_id, business_date, direction), UNIQUE(driver_id, business_date, direction) — aynı
    araç/şoför aynı gün aynı yönde ikinci sefere atanamaz (basit ve yeterli çakışma önleme kuralı).
- **attendance_event**(id, trip_id FK, student_id FK, event_type CHECK IN ('BOARDING','DROPOFF','ABSENCE'),
  recorded_at timestamptz, recorded_by_user_id FK, reason NULL, is_correction bool default false,
  corrected_event_id FK NULL)
  - UNIQUE(trip_id, student_id, event_type) — aynı sefer+öğrenci+tip tekrar edemez (düzeltme yeni satır+
    `is_correction` ile audit izli yapılır, var olan silinmez).
  - Uygulama kuralı: DROPOFF yazılmadan önce aynı trip+student için BOARDING var olmalı; ABSENCE varsa
    BOARDING/DROPOFF eklenemez (ve tersi) — service katmanında kontrol edilir.

## Sözleşme / Finans
- **contract**(id, student_id FK, monthly_fee numeric(12,2) CHECK > 0, discount_amount numeric(12,2)
  CHECK >= 0 default 0, start_date, end_date NULL,
  status CHECK IN ('ACTIVE','CANCELLED','EXPIRED','PENDING_APPROVAL'))
  - Çakışan iki ACTIVE sözleşme aynı student_id için engellenir (exclusion / uygulama kontrolü) —
    `PENDING_APPROVAL` durumundaki satırlar bu kontrole dahil DEĞİLDİR (henüz aktif değil).
  - `PENDING_APPROVAL`: 2026-10-25'te veli öz-kayıt sihirbazı için eklendi (V12) — veli formu
    gönderdiğinde `monthly_fee=institution_settings.default_monthly_fee` ile bu durumda oluşturulur;
    ADMIN `POST /api/contracts/{id}/approve` ile (ücreti teyit/değiştirerek) `ACTIVE`'e geçirir ya da
    `POST /api/contracts/{id}/reject` ile `CANCELLED`'a geçirir (bu durumda bağlı `student.is_active`
    de `false` yapılır, `parent_student_link` revoke edilir — hiçbir satır silinmez).
- **monthly_charge**(id, student_id FK, contract_id FK, period char(7) — 'YYYY-MM', amount numeric(12,2)
  CHECK > 0, created_at) — UNIQUE(student_id, period) → idempotent tahakkuk garantisi.
- **payment**(id, student_id FK, amount numeric(12,2) CHECK > 0, method, status CHECK IN
  ('COMPLETED','CANCELLED'), idempotency_key UNIQUE NULL, note, created_by_user_id FK, cancelled_at NULL,
  created_at)
  - Bakiye hesaplama: `SUM(monthly_charge.amount) - SUM(payment.amount WHERE status='COMPLETED')`,
    ödeme kaydı eklenirken aynı transaction + `SELECT ... FOR UPDATE` (pessimistic lock) ile kalan bakiye
    kontrol edilip aşım varsa `409` döner (eşzamanlı çift ödeme/fazla tahsilat önleme).

## Bildirim / Konum / Ayarlar
- **notification**(id, recipient_user_id FK, channel CHECK IN ('IN_APP','EMAIL'), title, body, is_read,
  created_at)
- **vehicle_location**(id, trip_id FK, lat, lng, recorded_at timestamptz, created_at)
  - Index(trip_id, recorded_at desc). Saklama: 90 gün — `VehicleLocationRetentionScheduler`
    (günlük 02:30 Europe/Istanbul) `recorded_at`'ı 90 günden eski kayıtları siler.
- **institution_settings**(id — tek satır, name, address, phone, report_header_note,
  default_monthly_fee numeric(12,2) NULL)
  - `default_monthly_fee`: 2026-10-25'te veli öz-kayıt sihirbazı için eklendi (V12) — ADMIN tarafından
    `/api/settings` üzerinden ayarlanır. `NULL` iken veli öz-kayıt akışı `409` ile reddedilir ("kurum
    henüz varsayılan ücret belirlememiş").

## Günlük operasyon (V14, 2026-11-18 — bkz. docs/API.md §21–§27)
- **trip** + `planned_start_time TIME NULL`, `cancellation_reason`, `cancelled_at`; index
  `(hostess_id, business_date, direction)` (hostes çakışma ön-kontrolü — SERIALIZABLE transaction'da).
- **notification** + `event_key VARCHAR(200) NULL`; kısmi benzersiz `uq_notification_recipient_channel_event
  (recipient_user_id, channel, event_key) WHERE event_key IS NOT NULL` — aynı operasyon olayı alıcıya bir kez.
- **contract** + `rejection_reason`.
- **trip_absence_notice**(trip_id, student_id, parent_id, note, status ACTIVE|WITHDRAWN, withdrawn_at, version)
  — veli "servise binmeyecek" bildirimi; resmi yoklama DEĞİL. Kısmi benzersiz `(trip_id, student_id) WHERE
  status='ACTIVE'`.
- **trip_incident**(trip_id, type DELAY|BREAKDOWN|ROAD_CLOSURE|CANCELLATION_REQUEST, status
  RECORDED|OPEN|APPROVED|REJECTED, delay_minutes 1..600, note, reported_by_user_id, resolution_note,
  resolved_by_user_id, resolved_at, version) — kısmi benzersiz `(trip_id) WHERE type='CANCELLATION_REQUEST'
  AND status='OPEN'`.
- **attendance_correction_request**(trip_id, student_id, kind CORRECTION|REVIEW, source_status,
  requested_status, reason, status PENDING|APPROVED|REJECTED, requested_by_user_id, decided_by_user_id,
  decided_at, decision_note, applied_status, version) — kısmi benzersiz `(trip_id, student_id) WHERE
  status='PENDING'`.
- **attendance_event** + `voided_at`, `voided_by_correction_id`, `created_by_correction_id`.
  `uq_attendance_event_trip_student_type` artık `WHERE is_correction = FALSE AND voided_at IS NULL`
  (yeniden oluşturuldu). Entity `@SQLRestriction("voided_at IS NULL")` — geçersiz kayıtlar hiçbir okumada
  görünmez, silinmez.
- **stop_change_request**(student_id, parent_id, requested_by_user_id, direction, requested_stop_id,
  effective_date, reason, status PENDING|APPROVED|REJECTED, decision_note, decided_by_user_id, decided_at,
  resulting_assignment_id, version) — kısmi benzersiz `(student_id, direction) WHERE status='PENDING'`.
- Kilit sırası: sefer satırı (`FOR UPDATE`) → talep/kayıt satırı → bağlı tablolar. Öğrenci bazlı tekillik
  kuralları (veli bildirimi, düzeltme talebi, durak talebi) öğrenci satırı kilidiyle; veli öz-kayıt başvuruları
  veli satırı kilidiyle serileştirilir.

## Audit
- **audit_log**(id, actor_user_id FK, action, entity_name, entity_id, detail jsonb, created_at) — kritik
  finans/yoklama düzeltmeleri ve rol/aktiflik değişiklikleri burada izlenir. 2026-11-18'den itibaren
  `ATTENDANCE_CORRECTION_APPROVED|REJECTED` (eski/yeni durum, gerekçe, talep eden, karar veren, zaman) ve
  `STOP_CHANGE_APPROVED` kayıtları `AuditLogService` ile işlemle aynı transaction'da yazılır.

> Not: Yukarıdaki model Faz B çıktısıdır; Backend Agent gerçek Flyway migration dosyalarını (`V1__...sql`,
> `V2__...sql`, ...) bu modele göre yazar. Uygulanmış migration dosyaları değiştirilmez, değişiklik yeni
> migration olarak eklenir. Gerçek constraint/index isimleri migration dosyalarında nihai haldedir.
