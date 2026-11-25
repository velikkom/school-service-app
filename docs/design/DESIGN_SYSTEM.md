# ServisFlow Tasarım Sistemi (Sprint 1)

Çalışan karşılığı: `docs/design/prototype/assets/styles.css` (token'lar `:root` altında). **Sprint 2 (uygulandı):**
token'lar `frontend/src/styles/tokens/_variables.scss` içinde CSS değişkeni olarak, kırılım mixin'leri
`frontend/src/styles/_tokens.scss`'te; PrimeReact teması `src/styles/base/_primereact.scss` ile bu değişkenlere
bağlandı. Bootstrap kaldırıldı; yerleşim CSS Grid/Flexbox. Kontrast düzeltmesi: `--ink-3` `#6b7581` → `#5b6571`
(kâğıt zeminde 4,25:1 idi, şimdi tüm zeminlerde ≥ 5:1).

## 1. Görsel kimlik

- **Karakter:** sakin, açık zeminli, "operasyon panosu" netliği. Referans portalın koyu lacivert + elektrik mavisi +
  turuncu dilini **almadık**; okul servisine özgü iki işaret seçtik: *servis yeşili* (güven, onay, birincil aksiyon) ve
  *okul servisi sarısı* (yalnızca araç işareti, aktif sefer şeridi ve sayaç rozetleri).
- **Ürün adı:** "ServisFlow" (geçici; tek kaynak `frontend/components/landing/config.js`). Logo işareti: sarı kare
  içinde servis ikonu. Portalın logo, maskot, otobüs illüstrasyonu ve metinleri kullanılmadı.
- Yönetici kenar çubuğu koyu (`--night`), içerik alanı açık kâğıt tonu (`--paper`). Saha ve veli ekranları tamamen açık
  zeminlidir; tek koyu öğe aktif sefer şerididir (güneş altında kontrast için).

## 2. Renk token'ları

| Token | Değer | Kullanım |
|---|---|---|
| `--paper` | `#f5f4ef` | Sayfa zemini |
| `--surface` / `--surface-2` / `--surface-sunken` | `#ffffff` / `#faf9f6` / `#eeede6` | Kart, tablo başlığı/filtre, segment zemini |
| `--line` / `--line-strong` | `#e2e0d7` / `#cfccc0` | Ayraç, form kenarı |
| `--ink` / `--ink-2` / `--ink-3` | `#18212b` / `#47525e` / `#6b7581` | Metin hiyerarşisi (ink-3 beyaz üzerinde 4.6:1) |
| `--brand` / `--brand-strong` / `--brand-soft` | `#0d6e5b` / `#0a5748` / `#e3f1ec` | Birincil buton, aktif sekme, odak halkası |
| `--bus` / `--bus-soft` | `#f0b20a` / `#fff6d9` | Araç işareti, aktif sefer şeridi, sıradaki durak, menü sayaçları |
| `--night` | `#15202b` | Yönetici kenar çubuğu, aktif sefer şeridi |

### Durum renkleri (her zaman metin + ikonla birlikte)

| Anlam | Ton (metin/zemin/kenar) | Örnek rozet |
|---|---|---|
| Başarılı / bindi / aktif | `--ok` `#17794a` | "Bindi", "Onaylandı", "Aktif" |
| Bilgi / planlandı / indi | `--info` `#2459c7` | "Planlandı", "İndi" |
| Uyarı / bekliyor / gecikme | `--warn` `#9a5b00` | "Bekliyor", "Gecikme +10 dk", "Karar bekliyor" |
| Tehlike / iptal / gelmedi / ret | `--danger` `#b5302a` | "İptal edildi", "Gelmedi", "Reddedildi" |
| **Veli bildirimi** (resmi değil) | `--notice` `#5c46b0` (mor) | "Veli: binmeyecek" |
| Nötr / tamamlandı / pasif | `--muted` `#59636e` | "Tamamlandı", "Pasif" |
| **Henüz işlem yok** | beyaz zemin + **kesik** kenar | "İşlem yok" |

Kural: Veli bildirimi asla kırmızı (resmi devamsızlık) ile aynı tonda gösterilmez. "İşlem yok" dolu renk almaz;
kesik kenarlı rozet ve satırın sol kenarında kesik çizgi ile ayrışır.

## 3. Tipografi

| Rol | Yazı tipi | Boyut / ağırlık |
|---|---|---|
| Arayüz | **Figtree** (Google Fonts; Next.js'te `next/font/google` ile self-host) — yedek: Segoe UI, system-ui | 15 px gövde, 13 px yardımcı, 12 px etiket |
| Sayısal / saat / plaka | **IBM Plex Mono** 500–700, `tabular-nums` | Saat 22–34 px, plaka 13 px |
| Başlık ölçeği | 27 / 21 / 17 / 15 px, 800 ağırlık, −0.01em harf aralığı | Sayfa / bölüm / kart başlığı |

Türkçe karakterler (ç, ğ, ı, İ, ö, ş, ü) her iki ailede test edildi. Baş harf üretiminde `toLocaleUpperCase("tr")`.

## 4. Boşluk, köşe, gölge

- 4 px ızgara: `--s1..--s10` = 4, 8, 12, 16, 20, 24, 32, 40.
- Köşe: 6 (küçük buton), 10 (input, buton), 14 (kart), 20 (dialog, hero kart), 999 (rozet).
- Gölge: `--shadow-1` kart, `--shadow-2` hero/toast, `--shadow-3` dialog.
- Sayfa iç boşluğu: masaüstü 24×28 px, mobil 16 px (alt menü için 96 px alt boşluk).

## 5. İkonlar

Prototipte elle çizilmiş 24 px çizgi ikonlar (`assets/icons.js`). Sprint 2'de PrimeIcons karşılıkları:

| Kavram | Prototip | PrimeIcons |
|---|---|---|
| Sefer / servis | `bus` | `pi pi-car` (veya özel SVG) |
| Güzergâh | `route` | `pi pi-directions` |
| Durak | `stop` / `pin` | `pi pi-map-marker` |
| Canlı | `live` | `pi pi-wifi` |
| Bindi / İndi | `board` / `drop` | `pi pi-arrow-up` / `pi pi-arrow-down` |
| Gelmedi / iptal | `absent` / `ban` | `pi pi-ban` |
| Veli bildirimi | `hand` | `pi pi-flag` |
| Gecikme | `delay` | `pi pi-clock` |
| Arıza / yol kapanması | `wrench` / `roadblock` | `pi pi-wrench` / `pi pi-exclamation-triangle` |
| Düzeltme talebi | `history` | `pi pi-history` |
| Talep / gelen kutusu | `inbox` | `pi pi-inbox` |
| Ödeme / makbuz / ekstre | `wallet` / `receipt` / `doc` | `pi pi-wallet` / `pi pi-receipt` / `pi pi-file` |
| Bildirim / toplu | `bell` / `megaphone` | `pi pi-bell` / `pi pi-megaphone` |

Mevcut `lib/icons.js` emoji setinin kalıcı olarak PrimeIcons/SVG'ye geçmesi önerilir (emoji ikonları platforma göre
farklı görünüyor ve durum rozetleriyle renk uyumu kurulamıyor). Aynı kavram her rolde aynı ikonu kullanır.

## 6. Bileşenler

| Bileşen | Tanım | PrimeReact eşleşmesi |
|---|---|---|
| **Buton** | primary (yeşil), secondary (beyaz+kenar), ghost, danger (dolu kırmızı), danger-outline, warn-outline; boyutlar sm 32, md 40, lg 52 px. İkon solda. | `Button` + `severity`, `outlined`, `text` |
| **Durum rozeti** | 24 px hap, ikon + metin, ton tablosu §2. Canlı rozetinde nabız noktası (reduced-motion'da durur). | `Tag` (özel sınıf) |
| **Plaka** | Mono yazı, mavi "TR" şeridi, siyah kenar. Plaka her zaman bu bileşenle gösterilir. | özel bileşen `VehiclePlate` |
| **Kart** | 14 px köşe, 1 px kenar, başlık satırı + gövde + alt satır. | `Card` veya div |
| **Metrik kartı** | Etiket + büyük sayı + ipucu; dikkat isteyen kartta sarı sol şerit. Tıklanır, ilgili listeye gider. | özel `MetricCard` |
| **Sefer kartı** | Saat (mono), yön rozeti, durum rozeti, güzergâh, plaka, şoför/hostes, gecikme/iptal talebi/veli bildirimi rozetleri, yoklama sayacı. Yoldaki sefer sarı sol şeritli. | özel `TripCard` |
| **Tablo / liste şablonu** | Filtre çubuğu (tarih, durum, arama, birincil aksiyon sağda) + tablo + sayfalayıcı. 900 px altında her satır etiketli karta dönüşür. | `DataTable` (responsiveLayout stack) |
| **Sekmeler** | Alt çizgili, sayaçlı. Detay ekranlarında `?tab=` ile adreslenir. | `TabView` veya route tabanlı |
| **Segment** | Kısa filtre seçimi (Bekleyen/Onaylanan/Reddedilen). | `SelectButton` |
| **Dialog** | Başlıkta tonlu ikon kutusu; gövdede uyarı kutusu + form; altta Vazgeç + aksiyon. Mobilde alttan açılan sayfa, butonlar tam genişlik ve dikey. Gerekçe zorunlu işlemlerde boş gönderimde satır içi hata. | `Dialog` (+ `position="bottom"` mobil) / `components/feedback/ReasonDialog.jsx` |
| **Uyarı kutusu** | info / warn / danger / ok / notice / muted. Başlık (kalın) + açıklama. | `Message` |
| **Toast** | Sağ alt (mobilde alt menünün üstü), 3.6 sn, başarı/hata. | `PrimeToast.jsx` |
| **Yolcu satırı (saha)** | Avatar + ad + rozetler; altında 48 px butonlar (Bindi / Gelmedi / İndi / Düzeltme iste). Sol kenar durumu kodlar: kesik gri = işlem yok, yeşil = bindi, mavi = indi, kırmızı = gelmedi; veli bildirimi satırı mor zemin geçişi + not kutusu. | özel `RosterRow` |
| **Durak başlığı** | Sıra numarası dairesi, durak adı, planlı saat (mono); sıradaki durak sarı daire + "Sıradaki durak" rozeti; listede yapışkan. | özel `StopHeader` |
| **Durak zaman çizelgesi** | Saat – işaret – ad – öğrenci sayısı; geçilen duraklar yeşil, mevcut sarı, kendi durağı mor rozetli. | `Timeline` |
| **Aktif sefer şeridi** | Koyu kart, sarı sol şerit, güzergâh, plaka, 4 sayaç (bindi/indi/gelmedi/bekleyen), tam genişlik sarı aksiyon. | özel |
| **Hero servis kartı (veli/öğrenci)** | Yön + plaka, durak saatinin büyük mono gösterimi, durak satırı, durum rozetleri, gecikme/iptal/bildirim kutuları, harita, aksiyonlar. | özel `DailyServiceCard` (mevcut bileşen yeniden tasarlanır) |
| **Harita paneli** | Üst solda "Canlı / Bağlantı yok" + "Son konum: SS:DD" + "Güncellendi: N sn önce" damgası; sağ altta açıklama. Boş durumlar: sefer başlamadı, konum yok, aktif değil. ETA gösterilmez. | Sprint 2 harita kütüphanesi kararı |
| **Çocuk seçici** | Yatay kaydırılabilir hap listesi, sonda kesik kenarlı "Öğrenci ekle". | `SelectButton` / özel |
| **Adım göstergesi** | Çubuklu 4 adım (sihirbaz, onay bekleme). | `Steps` |
| **Ekran durumları** | Yükleniyor (iskelet satırlar, `aria-busy`), Boş (ikon + başlık + açıklama + isteğe bağlı aksiyon), Hata (hata kodu + Tekrar dene), Yetki yok (kilit, "Ana sayfaya dön"). | özel `ScreenState` |

## 7. Menüler

- **Masaüstü (≥ 900 px):** 264 px koyu kenar çubuğu, gruplu (Operasyon, Talepler ve onaylar, Yönetim, Finans,
  İletişim ve ayarlar), bekleyen sayaçları sarı. Üst çubuk: kırıntı + sayfa başlığı, tarih çipi, bildirim zili
  (okunmamış sayısı), kullanıcı.
- **Mobil (< 900 px):** üstte geri/başlık/alt başlık + zil; altta 4 ana öğe + "Daha fazla" (tüm menü alttan sayfa
  olarak). Saha: Bugün, Yoklama, Güzergâh, Talepler. Veli: Servis, Yoklama, Talepler, Ödemeler. Öğrenci: Servis,
  Güzergâh, Yoklama, Bildirim. Yönetici: Operasyon, Seferler, Talepler, Bildirim.

## 8. Responsive kurallar

| Kırılım | Davranış |
|---|---|
| ≥ 1180 px | Ana + yan sütun (`g-main` 1.65fr / 1fr), 4'lü metrik ızgarası |
| 900–1179 px | Yan sütun alta iner, metrikler 2'li |
| < 900 px | Kenar çubuğu → alt menü; tablolar → etiketli kart listesi; dialoglar → alttan sayfa; formlar tek sütun |
| 360 px | Saha ve veli ekranları tek sütun; yoklama butonları 2–3'lü ızgarada en az 48 px; plaka ve rozetler satır kırar |

Kritik ekranlar 1440 ve 390 px'te, saha/veli ekranları ayrıca 360 px'te çekildi (`mockups/`). Prototipte kırılımlar
viewport değil `app` container'ına bağlıdır; Next.js'te aynı değerler media query olarak kullanılabilir.

## 9. Etkileşim ve erişilebilirlik kuralları

- Durum asla yalnız renkle anlatılmaz: her rozet metin + ikon taşır; satır kenarı da desen (kesik/düz) farkı içerir.
- Saha ve veli ekranlarında dokunma hedefi en az 48 px; birincil aksiyonlar başparmak bölgesinde (yapışkan alt
  aksiyon çubuğu).
- Geri alınamaz işlemler (iptal, ret, silme, toplu bildirim, iptal talebi onayı) her zaman onay dialogu; ret ve iptal
  gerekçesi zorunlu alan.
- Odak halkası `--focus` (3 px yeşil); dialoglar `role="dialog"`, `aria-modal`, Esc ile kapanır; toast'lar
  `aria-live="polite"`.
- Hareket: yalnızca canlı nabız ve iskelet parıltısı; `prefers-reduced-motion` ile kapanır.
- Tarih/saat Europe/Istanbul, biçim `dd.MM.yyyy` ve `HH:mm`; para `1.250,00 TL`.
- Dil Türkçe; enum değerleri arayüzde asla ham gösterilmez (eşleme: `assets/ui.js` → `LABELS`).
