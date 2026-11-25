# Gereksinimler

## Ürün
Tek bir okul servis firması için öğrenci/veli, araç/şoför/hostes, güzergâh/durak, sefer, yoklama,
sözleşme/ücret/ödeme, bildirim, konum takibi ve raporlama yapan web uygulaması.

**Multi-tenant değildir.** Firma bilgisi `settings` tablosunda tek satır olarak tutulur. Firma seçimi,
companyId/tenantId ayrımı, firma üyeliği/aboneliği, COMPANY_ADMIN/SUPER_ADMIN/OPERATOR rolleri yoktur.

## Roller
`ADMIN`, `DRIVER`, `HOSTESS`, `PARENT`, `STUDENT` (detay: docs/RBAC_MATRIX.md).

> Karar: Kullanıcıyla netleştirme sonucu **STUDENT** rolü eklendi (öğrenci kendi hesabıyla giriş yapıp
> salt-okunur kendi verilerine erişebilir). Bkz. docs/KNOWN_LIMITATIONS.md "Karar Günlüğü".

## Kapsam dışı (explicit olarak İSTENMEYEN)
- Multi-tenant / firma seçimi / companyId ayrımı
- Public self-register
- SMS veya ücretli 3. parti entegrasyon (yalnızca ileride eklenebilecek bir arayüz/sınır tanımlanır,
  gerçek SMS gönderimi yapılmaz)
- Mikroservis, message broker, Kubernetes
- "Her sınıfa interface" tarzı yapay SOLID uygulaması

## Fonksiyonel kapsam (özet)
Detaylı modül listesi ve iş kuralları için: docs/API.md, docs/RBAC_MATRIX.md, docs/DATABASE_DESIGN.md.
Bu belge üst seviye referans amaçlıdır; madde tekrarından kaçınmak için kurallar ilgili belgelerde tutulur.
