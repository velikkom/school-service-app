# Sprint 2 — Frontend geliştirme kılavuzu (yeni yapı)

Bu belge `frontend/` yeniden yazımında her ekranın uyduğu kuralları ve hazır ortak parçaları listeler.
Görsel referans: `docs/design/DESIGN_SYSTEM.md` + `docs/design/prototype/` (özellikle `assets/screens-*.js`,
`assets/styles.css`). API sözleşmesi: `docs/API.md`, yetki matrisi: `docs/RBAC_MATRIX.md`.

## 1. Teknoloji ve kurallar

- Next.js 16 App Router, React 19, **JavaScript** (TS yok). JSX içeren dosyalar `.jsx`, saf JS `.js`.
- PrimeReact 10 (`primereact/<bileşen>`) + PrimeIcons (`pi pi-...`). Bootstrap, react-bootstrap, sweetalert2,
  Tailwind veya başka UI kütüphanesi **yok**. Yeni npm paketi ekleme.
- Stil: bileşen başına `*.module.scss` (feature içinde `style/` klasörü), ilk satır `@use "tokens" as *;`
  (mixin'ler: `mobile`, `desktop`, `below-wide`, `narrow`, `card`, `mono`, `reduced-motion`). Renk/boşluk
  yalnızca CSS değişkenleri (`var(--brand)`, `var(--s4)`, `var(--r-lg)` …; liste `src/styles/tokens/_variables.scss`).
  Sabit hex renk yazma. Genel yardımcı sınıflar: `stack`, `stack-lg`, `row`, `between`, `grid-2/3/4`,
  `grid-main`, `hide-mobile`, `show-mobile`, `mono`, `muted`, `text-sm`, `sr-only`.
- Etkileşimli bileşenlerin başında `"use client"`. `use server` / Server Action kullanılmaz.
- Metinler Türkçe. Tarih/saat/para yalnızca `@/lib/formatting/date` (`formatDate`, `formatDateTime`,
  `formatClock`, `formatTime` (LocalTime), `formatLongDate`, `formatPeriod`, `formatAgo`,
  `formatRelativeDateTime`, `todayISO`, `addDaysISO`) ve `@/lib/formatting/money` (`formatMoney`, `initials`).
- Enum'lar asla ham gösterilmez: `@/lib/formatting/labels` → `labelOf(group, value)`, `optionsOf(group)`,
  `ROLE_LABELS`, `PAYMENT_METHOD_LABELS`, `GENDER_LABELS`, `mapOptions`. Gruplar: `tripStatus`, `attendance`
  (NONE/BOARDED/DROPPED_OFF/ABSENT), `attendanceEvent`, `direction`, `incidentType`, `incidentStatus`,
  `requestStatus`, `correctionKind`, `absenceNoticeStatus`, `contractStatus`, `registrationStatus`,
  `paymentStatus`, `active`, `role`.
- Mobil: < 900 px. Saha ve veli aksiyonları en az 48 px (`min-height: var(--tap)`). Yatay kaydırma yok
  (1440/390/360). İçerik alanı alt menü için zaten alt boşluk bırakır; yapışkan alt aksiyon çubuğu
  kullanıyorsan mobilde `bottom: calc(var(--bottom-nav-h) + 8px)` ile alt menünün üstünde dursun ve son
  satırı kapatmasın (liste sonuna çubuk yüksekliği kadar boşluk ekle).
- Erişilebilirlik: durum her zaman metin + ikon; ikon-only butonlara `aria-label`; form alanları `Field` ile.

## 2. Klasör ve sahiplik

```
src/app/(protected)/<route>/page.jsx   → ince sayfa: bileşen seçimi (rol bazlı olanlar koordinatörde)
src/features/<özellik>/
  service/   API çağrıları (HAZIR — koordinatör yazdı; eksik uç gerekirse aynı dosyaya ekle)
  hooks/     sorgu anahtarları (keys.js) + useQuery hook'ları
  action/    useMutation hook'ları (kullanıcı işlemleri, invalidation, toast)
  schemas/   Zod şemaları
  components/ ekran ve parçaları (ekran bileşeni adı …Screen.jsx)
  style/     *.module.scss
```
Yalnızca sana verilen klasör/dosyalarda çalış. Ortak dosyalar (`src/components/**`, `src/lib/**`,
`src/styles/**`, `src/providers/**`, `src/app/layout.jsx`, rol bazlı sayfalar, `package.json`) koordinatörde;
ihtiyaç olursa raporunda belirt. Başka bir feature'ın yalnızca `service/` dosyasını import edebilirsin; başka
feature'ın `components/hooks/action` iç dosyalarına bağımlı olma.

## 3. Hazır ortak parçalar

| Import | Kullanım |
|---|---|
| `@/lib/http/client` → `http.get/post/patch/del` | Sadece service/ içinde. Hata `ApiError` (`status`, `error`, `message`, `fieldErrors`). |
| `@/lib/http/apiError` → `errorMessage(err, fallback)`, `isConflict`, `isNotFound` | Toast/inline hata metni (409 `CONCURRENT_CONFLICT` için "tekrar dene" metni üretir). |
| `@/lib/http/applyServerErrors` → `applyServerFieldErrors(err, setError)` | 400 `fieldErrors` → RHF alanları. |
| `@/lib/http/page` → `toList(resp)`, `pageMeta(resp)` | Sayfalı/düz dizi yanıtı. |
| `@/lib/http/document` → `openHtmlDocument(path)` | Makbuz/ekstre "Aç / yazdır" (PDF deme). |
| `@/providers/AuthProvider` → `useAuth()` | `{ user: {id, fullName, email, role}, role, logout }` |
| `@/components/layout/PageMeta` | `<PageMeta title subtitle back />` — kabuk başlığı (sayfada ayrıca h1 yazma). |
| `@/components/feedback/ScreenState` | `kind`: loading/empty/error/forbidden/notFound; `QueryState` yardımcı (403→yetki yok, 404→bulunamadı). |
| `@/components/feedback/AppDialog` | Ortak dialog (tonlu ikon, mobilde alttan sayfa, odak dönüşü, `onSubmit` + `submitting` ile çift gönderim engeli). |
| `@/components/feedback/ReasonDialog` | Gerekçe zorunlu ret/iptal dialogu (`onConfirm(reason)`). |
| `@/components/feedback/feedback` | `toastSuccess`, `toastError`, `toastInfo`, `confirmAction({title,message,acceptLabel,tone})` → Promise<boolean>. |
| `@/components/ui/Field` (+`FormGrid`) | Etiketli alan, hata/yardım metni, aria bağları. |
| `@/components/ui/StatusBadge` (+`NoticeBadge`) | `group`+`value` veya `tone/icon/label`. |
| `@/components/ui/VehiclePlate` | Plaka her zaman bununla. |
| `@/components/ui/Card` (+`MetricCard`, `DefinitionList`) | Kart, metrik kartı, tanım listesi. |
| `@/components/ui/Notice` (+`Avatar`) | Uyarı kutusu (info/warn/danger/ok/notice/muted). |
| `@/components/ui/LocationPanel` | Son konum paneli; `@/features/tracking/hooks/useLatestLocation(tripId)` ile (404 → `null`). |
| `@/components/ui/useIsMobile` | < 900 px. |

## 4. Veri kuralları

- Sorgu anahtarları feature `hooks/keys.js` içinde; kapsamı içermeli: ör. `["trips","roster",tripId]`,
  `["daily-service",studentId,date]`, `["attendance",studentId,from,to]`. Rol değişiminde önbellek AuthProvider'da
  temizlenir.
- Mutasyon sonrası etkilenen anahtarları `invalidateQueries` ile yenile (ör. yoklama → roster + trip;
  düzeltme kararı → corrections + roster + `pending-counts`). Yenileme sırasında ekranı boşaltma
  (`placeholderData: keepPreviousData` veya mevcut veriyi göster).
- Canlı ekranlar polling ile güncellenir: saha yolcu listesi ve ADMIN sefer detayı 15 sn, veli günlük servis
  30 sn, konum 15 sn, bekleyen sayaçlar/zil 60 sn. Ayrıca pencereye dönüşte yeniden çekilir.
- Butonları rol + API bayraklarına göre göster (`canSubmitAbsenceNotice`, `pendingCorrectionId`,
  `openCancellationRequestId`, sefer `status` …). Backend son karardır; 403/404/409 mesajını kullanıcıya göster.
- 404 bağlama göre: "atanmış araç yok" → boş durum; kapsam dışı kayıt → `notFound`; aktif sefer yok →
  açıklama + Bugün'e dön.
- Formlar: React Hook Form + Zod (`zodResolver`), gönderimde buton `loading`+`disabled`.
- Prototipteki araç çubuğu, sentetik veri, demo rol değiştirici ve "Yeni API gerekir" etiketli özellikler
  uygulanmaz; ETA, toplu konum, durak koordinatı gibi veriler uydurulmaz.

## 5. Doğrulama (agent'lar)

`next build`/`next dev` çalıştırma (koordinatör tek seferde çalıştırır). Kendi dosyaların için:
`npx eslint src/features/<senin klasörlerin> "src/app/(protected)/<senin route'ların>"` temiz olmalı;
yazdığın birim testleri `tests/unit/<alan>/*.test.jsx` altında, `npx vitest run tests/unit/<alan>` ile çalıştır.
