/**
 * Doğrudan URL erişimi için rol → route kuralları (UX katmanı; nihai karar backend'de).
 * Bir route, listedeki en uzun önek eşleşmesine göre değerlendirilir.
 */
export const ROLES = ["ADMIN", "DRIVER", "HOSTESS", "PARENT", "STUDENT"];
export const FIELD_ROLES = ["DRIVER", "HOSTESS"];
export const FAMILY_ROLES = ["PARENT", "STUDENT"];

const ALL = ROLES;
const ADMIN = ["ADMIN"];

const RULES = [
  ["/dashboard", ALL],
  ["/profil", ALL],
  ["/bildirimler", ALL],
  ["/bildirimler/toplu", ADMIN],
  ["/seferler", ["ADMIN", "DRIVER", "HOSTESS"]],
  ["/yoklama", ["DRIVER", "HOSTESS", "PARENT", "STUDENT"]],
  ["/konum", ALL],
  ["/servisim", ["PARENT", "STUDENT"]],
  ["/taleplerim", ["PARENT"]],
  ["/ogrenci-ekle", ["PARENT"]],
  ["/yoklama-duzeltmeleri", ["ADMIN", "DRIVER", "HOSTESS", "PARENT"]],
  ["/talepler", ADMIN],
  ["/veli-talepleri", ADMIN],
  ["/durak-talepleri", ADMIN],
  ["/ogrenci-kayit-talepleri", ADMIN],
  ["/kayit-talepleri", ADMIN],
  ["/ogrenciler", ["ADMIN", "PARENT", "STUDENT"]],
  ["/veliler", ADMIN],
  ["/soforler", ADMIN],
  ["/hostesler", ADMIN],
  ["/araclar", ["ADMIN", "DRIVER"]],
  ["/okullar", ADMIN],
  ["/guzergahlar", ["ADMIN", "DRIVER", "HOSTESS"]],
  ["/atamalar", ["ADMIN", "STUDENT"]],
  ["/kullanicilar", ADMIN],
  ["/sozlesmeler", ["ADMIN", "PARENT"]],
  ["/tahakkuklar", ["ADMIN", "PARENT"]],
  ["/odemeler", ["ADMIN", "PARENT"]],
  ["/raporlar", ADMIN],
  ["/ayarlar", ADMIN],
];

export function canAccessPath(role, pathname) {
  if (!role) return false;
  let best = null;
  for (const [prefix, roles] of RULES) {
    if ((pathname === prefix || pathname.startsWith(`${prefix}/`)) && (!best || prefix.length > best[0].length)) {
      best = [prefix, roles];
    }
  }
  // Tanımsız route'lar not-found ile karşılanır; burada engellenmez.
  return best ? best[1].includes(role) : true;
}

/** Rolün ilk ekranı. */
export function homePathFor(role) {
  switch (role) {
    case "ADMIN":
      return "/dashboard";
    case "DRIVER":
    case "HOSTESS":
      return "/seferler";
    case "PARENT":
    case "STUDENT":
      return "/servisim";
    default:
      return "/login";
  }
}

export const isFieldRole = (role) => FIELD_ROLES.includes(role);
export const isFamilyRole = (role) => FAMILY_ROLES.includes(role);
