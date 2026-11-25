/**
 * Enum → arayüz etiketi + ton + PrimeIcons ikonu. Enum değerleri arayüzde asla ham gösterilmez.
 * Tonlar: ok | info | warn | danger | notice | muted | none | live  (DESIGN_SYSTEM.md §2)
 */
export const ROLE_LABELS = { ADMIN: "Yönetici", DRIVER: "Şoför", HOSTESS: "Hostes", PARENT: "Veli", STUDENT: "Öğrenci" };

export const LABELS = {
  tripStatus: {
    PLANNED: { label: "Planlandı", tone: "info", icon: "pi-calendar" },
    IN_PROGRESS: { label: "Yolda", tone: "live", icon: "pi-wifi" },
    COMPLETED: { label: "Tamamlandı", tone: "muted", icon: "pi-check-circle" },
    CANCELLED: { label: "İptal edildi", tone: "danger", icon: "pi-ban" },
  },
  attendance: {
    NONE: { label: "İşlem yok", tone: "none", icon: "pi-clock" },
    BOARDED: { label: "Bindi", tone: "ok", icon: "pi-arrow-up" },
    DROPPED_OFF: { label: "İndi", tone: "info", icon: "pi-arrow-down" },
    ABSENT: { label: "Gelmedi", tone: "danger", icon: "pi-ban" },
  },
  attendanceEvent: {
    BOARDING: { label: "Biniş", tone: "ok", icon: "pi-arrow-up" },
    DROPOFF: { label: "İniş", tone: "info", icon: "pi-arrow-down" },
    ABSENCE: { label: "Devamsızlık", tone: "danger", icon: "pi-ban" },
  },
  direction: {
    MORNING: { label: "Gidiş (sabah)", short: "Gidiş", tone: "muted", icon: "pi-sun" },
    AFTERNOON: { label: "Dönüş (akşam)", short: "Dönüş", tone: "muted", icon: "pi-moon" },
  },
  incidentType: {
    DELAY: { label: "Gecikme", tone: "warn", icon: "pi-clock" },
    BREAKDOWN: { label: "Arıza", tone: "danger", icon: "pi-wrench" },
    ROAD_CLOSURE: { label: "Yol kapanması", tone: "warn", icon: "pi-exclamation-triangle" },
    CANCELLATION_REQUEST: { label: "İptal talebi", tone: "danger", icon: "pi-ban" },
  },
  incidentStatus: {
    RECORDED: { label: "Kaydedildi", tone: "muted", icon: "pi-check" },
    OPEN: { label: "Karar bekliyor", tone: "warn", icon: "pi-clock" },
    APPROVED: { label: "Onaylandı", tone: "ok", icon: "pi-check-circle" },
    REJECTED: { label: "Reddedildi", tone: "danger", icon: "pi-times" },
  },
  requestStatus: {
    PENDING: { label: "Bekliyor", tone: "warn", icon: "pi-clock" },
    APPROVED: { label: "Onaylandı", tone: "ok", icon: "pi-check-circle" },
    REJECTED: { label: "Reddedildi", tone: "danger", icon: "pi-times" },
  },
  correctionKind: {
    CORRECTION: { label: "Düzeltme", tone: "info", icon: "pi-history" },
    REVIEW: { label: "İnceleme", tone: "notice", icon: "pi-search" },
  },
  absenceNoticeStatus: {
    ACTIVE: { label: "Veli: binmeyecek", tone: "notice", icon: "pi-flag" },
    WITHDRAWN: { label: "Geri alındı", tone: "muted", icon: "pi-undo" },
  },
  contractStatus: {
    ACTIVE: { label: "Aktif", tone: "ok", icon: "pi-check-circle" },
    PENDING_APPROVAL: { label: "Onay bekliyor", tone: "warn", icon: "pi-clock" },
    CANCELLED: { label: "İptal / ret", tone: "danger", icon: "pi-ban" },
    EXPIRED: { label: "Süresi doldu", tone: "muted", icon: "pi-history" },
  },
  registrationStatus: {
    PENDING_EMAIL_VERIFICATION: { label: "E-posta bekleniyor", tone: "muted", icon: "pi-envelope" },
    EMAIL_VERIFIED: { label: "Onay bekliyor", tone: "warn", icon: "pi-clock" },
    APPROVED: { label: "Onaylandı", tone: "ok", icon: "pi-check-circle" },
    REJECTED: { label: "Reddedildi", tone: "danger", icon: "pi-times" },
  },
  paymentStatus: {
    COMPLETED: { label: "Tamamlandı", tone: "ok", icon: "pi-check-circle" },
    CANCELLED: { label: "İptal", tone: "danger", icon: "pi-ban" },
  },
  active: {
    true: { label: "Aktif", tone: "ok", icon: "pi-check" },
    false: { label: "Pasif", tone: "muted", icon: "pi-ban" },
  },
  role: {
    ADMIN: { label: "Yönetici", tone: "info", icon: "pi-shield" },
    DRIVER: { label: "Şoför", tone: "muted", icon: "pi-car" },
    HOSTESS: { label: "Hostes", tone: "muted", icon: "pi-user" },
    PARENT: { label: "Veli", tone: "muted", icon: "pi-users" },
    STUDENT: { label: "Öğrenci", tone: "muted", icon: "pi-id-card" },
  },
};

export const PAYMENT_METHOD_LABELS = { CASH: "Nakit", BANK_TRANSFER: "Havale/EFT", CARD: "Kart", OTHER: "Diğer" };
export const GENDER_LABELS = { MALE: "Erkek", FEMALE: "Kız" };

/**
 * Veli–öğrenci yakınlığı. API'de serbest metin: ADMIN formları kod (ANNE…), veli sihirbazı Türkçe
 * metin ("Anne") gönderir; ikisi de aynı etikete çözülür.
 */
export const RELATION_OPTIONS = [
  { value: "ANNE", label: "Anne" },
  { value: "BABA", label: "Baba" },
  { value: "VASI", label: "Vasi" },
  { value: "DIGER", label: "Diğer" },
];

export function relationLabel(value) {
  if (!value) return "—";
  const key = String(value).toLocaleUpperCase("tr").replace("İ", "I").replace("Ğ", "G");
  return RELATION_OPTIONS.find((o) => o.value === key)?.label ?? String(value);
}

/** Bilinmeyen değerde ham enum yerine nötr bir etiket döner. */
export function labelOf(group, value) {
  const entry = LABELS[group]?.[String(value)];
  return entry ?? { label: value == null ? "—" : "Bilinmiyor", tone: "muted", icon: "pi-question-circle" };
}

/** { value, label } seçenekleri (Dropdown / SelectButton). */
export function optionsOf(group) {
  return Object.entries(LABELS[group] ?? {}).map(([value, e]) => ({ value, label: e.label }));
}

export function mapOptions(map) {
  return Object.entries(map).map(([value, label]) => ({ value, label }));
}
