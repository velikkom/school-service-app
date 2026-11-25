/**
 * Rol bazlı menüler — docs/design/SCREEN_MATRIX.md §7. Yalnızca UX içindir; nihai yetki backend'dedir.
 * Her öğe: { key, label, short?, href, icon, countKey? } — countKey kabukta bekleyen sayaç bağlar.
 */
const ADMIN = [
  {
    group: "Operasyon",
    items: [
      { key: "operasyon", label: "Günlük Operasyon", short: "Operasyon", href: "/dashboard", icon: "pi-th-large" },
      { key: "seferler", label: "Seferler", short: "Seferler", href: "/seferler", icon: "pi-car" },
      { key: "konum", label: "Canlı Konum", href: "/konum", icon: "pi-map-marker" },
    ],
  },
  {
    group: "Talepler ve onaylar",
    items: [
      { key: "talepler", label: "Talep Merkezi", short: "Talepler", href: "/talepler", icon: "pi-inbox", countKey: "all" },
      { key: "yoklama-duzeltmeleri", label: "Yoklama Düzeltmeleri", href: "/yoklama-duzeltmeleri", icon: "pi-history", countKey: "corrections" },
      { key: "durak-talepleri", label: "Durak Değişiklikleri", href: "/veli-talepleri", icon: "pi-map-marker", countKey: "stopChanges" },
      { key: "ogrenci-basvurulari", label: "Öğrenci Başvuruları", href: "/ogrenci-kayit-talepleri", icon: "pi-id-card", countKey: "enrollments" },
      { key: "kayit-talepleri", label: "Hesap Kayıt Talepleri", href: "/kayit-talepleri", icon: "pi-envelope", countKey: "registrations" },
    ],
  },
  {
    group: "Yönetim",
    items: [
      { key: "ogrenciler", label: "Öğrenciler", href: "/ogrenciler", icon: "pi-id-card" },
      { key: "veliler", label: "Veliler", href: "/veliler", icon: "pi-users" },
      { key: "soforler", label: "Şoförler", href: "/soforler", icon: "pi-user" },
      { key: "hostesler", label: "Hostesler", href: "/hostesler", icon: "pi-user" },
      { key: "araclar", label: "Araçlar", href: "/araclar", icon: "pi-car" },
      { key: "okullar", label: "Okullar", href: "/okullar", icon: "pi-building" },
      { key: "guzergahlar", label: "Güzergâhlar ve Duraklar", href: "/guzergahlar", icon: "pi-directions" },
      { key: "atamalar", label: "Öğrenci Atamaları", href: "/atamalar", icon: "pi-link" },
      { key: "kullanicilar", label: "Kullanıcılar", href: "/kullanicilar", icon: "pi-users" },
    ],
  },
  {
    group: "Finans",
    items: [
      { key: "sozlesmeler", label: "Sözleşmeler", href: "/sozlesmeler", icon: "pi-file" },
      { key: "tahakkuklar", label: "Tahakkuklar", href: "/tahakkuklar", icon: "pi-receipt" },
      { key: "odemeler", label: "Ödemeler", href: "/odemeler", icon: "pi-wallet" },
      { key: "raporlar", label: "Raporlar", href: "/raporlar", icon: "pi-chart-bar" },
    ],
  },
  {
    group: "İletişim ve ayarlar",
    items: [
      { key: "bildirimler", label: "Bildirimler", short: "Bildirim", href: "/bildirimler", icon: "pi-bell" },
      { key: "toplu-bildirim", label: "Toplu Bildirim", href: "/bildirimler/toplu", icon: "pi-megaphone" },
      { key: "ayarlar", label: "Kurum Ayarları", href: "/ayarlar", icon: "pi-cog" },
    ],
  },
];

const fieldMenu = (withVehicle) => [
  {
    group: "Saha",
    items: [
      { key: "bugun", label: "Bugün", short: "Bugün", href: "/seferler", icon: "pi-home" },
      { key: "yoklama", label: "Yoklama", short: "Yoklama", href: "/yoklama", icon: "pi-check-circle" },
      { key: "guzergahim", label: "Güzergâhım", short: "Güzergâh", href: "/guzergahlar", icon: "pi-directions" },
      ...(withVehicle ? [{ key: "aracim", label: "Aracım", href: "/araclar", icon: "pi-car" }] : []),
      { key: "duzeltme-taleplerim", label: "Düzeltme Taleplerim", short: "Talepler", href: "/yoklama-duzeltmeleri", icon: "pi-history" },
      { key: "bildirimler", label: "Bildirimler", href: "/bildirimler", icon: "pi-bell" },
      { key: "profil", label: "Profil", href: "/profil", icon: "pi-user" },
    ],
  },
];

const PARENT = [
  {
    group: "Servis",
    items: [
      { key: "servisim", label: "Bugünkü Servis", short: "Servis", href: "/servisim", icon: "pi-car" },
      { key: "yoklama-gecmisi", label: "Yoklama Geçmişi", short: "Yoklama", href: "/yoklama", icon: "pi-history" },
      { key: "taleplerim", label: "Taleplerim", short: "Talepler", href: "/taleplerim", icon: "pi-inbox" },
    ],
  },
  {
    group: "Hesap",
    items: [
      { key: "odemeler", label: "Ödemeler ve Ekstre", short: "Ödemeler", href: "/odemeler", icon: "pi-wallet" },
      { key: "cocuklarim", label: "Çocuklarım", href: "/ogrenciler", icon: "pi-id-card" },
      { key: "ogrenci-ekle", label: "Öğrenci Ekle", href: "/ogrenci-ekle", icon: "pi-plus" },
      { key: "bildirimler", label: "Bildirimler", href: "/bildirimler", icon: "pi-bell" },
      { key: "profil", label: "Profil", href: "/profil", icon: "pi-user" },
    ],
  },
];

const STUDENT = [
  {
    group: "Öğrenci",
    items: [
      { key: "servisim", label: "Bugünkü Servisim", short: "Servis", href: "/servisim", icon: "pi-car" },
      { key: "guzergahim", label: "Güzergâhım", short: "Güzergâh", href: "/atamalar", icon: "pi-directions" },
      { key: "yoklama-gecmisi", label: "Yoklama Geçmişim", short: "Yoklama", href: "/yoklama", icon: "pi-history" },
      { key: "bildirimler", label: "Bildirimler", short: "Bildirim", href: "/bildirimler", icon: "pi-bell" },
      { key: "profil", label: "Bilgilerim", href: "/profil", icon: "pi-user" },
    ],
  },
];

const MENUS = { ADMIN, DRIVER: fieldMenu(true), HOSTESS: fieldMenu(false), PARENT, STUDENT };

/** Mobil alt menüdeki 4 ana öğe (5.si "Daha fazla"). */
const BOTTOM = {
  ADMIN: ["operasyon", "seferler", "talepler", "bildirimler"],
  DRIVER: ["bugun", "yoklama", "guzergahim", "duzeltme-taleplerim"],
  HOSTESS: ["bugun", "yoklama", "guzergahim", "duzeltme-taleplerim"],
  PARENT: ["servisim", "yoklama-gecmisi", "taleplerim", "odemeler"],
  STUDENT: ["servisim", "guzergahim", "yoklama-gecmisi", "bildirimler"],
};

export function getMenuGroups(role) {
  return MENUS[role] ?? [];
}

export function getMenuItems(role) {
  return getMenuGroups(role).flatMap((g) => g.items);
}

export function getBottomItems(role) {
  const items = getMenuItems(role);
  return (BOTTOM[role] ?? []).map((k) => items.find((i) => i.key === k)).filter(Boolean);
}

/** Verilen yol için en uzun önek eşleşmesiyle aktif menü öğesi. */
export function findActiveItem(role, pathname) {
  let best = null;
  for (const item of getMenuItems(role)) {
    const match = pathname === item.href || pathname.startsWith(`${item.href}/`);
    if (match && (!best || item.href.length > best.href.length)) best = item;
  }
  return best;
}
