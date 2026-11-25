// Tarih/saat biçimleri — her zaman Europe/Istanbul ve tr-TR.
export const TIME_ZONE = "Europe/Istanbul";

function toDate(value) {
  if (!value) return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  // Saf LocalDate ("2026-11-18") UTC gece yarısı olarak kaymasın diye öğlen kabul edilir.
  const v = /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T12:00:00Z` : value;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

const isoDayFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** Europe/Istanbul'a göre bugün, "YYYY-MM-DD". */
export function todayISO(now = new Date()) {
  return isoDayFormatter.format(now);
}

export function addDaysISO(iso, days) {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** "dd.MM.yyyy" */
export function formatDate(value) {
  const d = toDate(value);
  if (!d) return "—";
  return new Intl.DateTimeFormat("tr-TR", { timeZone: TIME_ZONE, day: "2-digit", month: "2-digit", year: "numeric" }).format(d);
}

/** "18 Kasım 2026 Çarşamba" */
export function formatLongDate(value) {
  const d = toDate(value);
  if (!d) return "—";
  return new Intl.DateTimeFormat("tr-TR", {
    timeZone: TIME_ZONE,
    day: "numeric",
    month: "long",
    year: "numeric",
    weekday: "long",
  }).format(d);
}

/** Instant → "HH:mm" (Europe/Istanbul) */
export function formatClock(value) {
  const d = toDate(value);
  if (!d) return "—";
  return new Intl.DateTimeFormat("tr-TR", { timeZone: TIME_ZONE, hour: "2-digit", minute: "2-digit", hour12: false }).format(d);
}

/** Instant → "dd.MM.yyyy HH:mm" */
export function formatDateTime(value) {
  const d = toDate(value);
  if (!d) return "—";
  return `${formatDate(d)} ${formatClock(d)}`;
}

/** Backend LocalTime ("07:30:00") → "07:30" */
export function formatTime(value) {
  if (!value || typeof value !== "string") return "—";
  const m = /^(\d{1,2}):(\d{2})/.exec(value);
  return m ? `${m[1].padStart(2, "0")}:${m[2]}` : "—";
}

/** YearMonth "2026-11" → "Kasım 2026" */
export function formatPeriod(value) {
  if (!value) return "—";
  const [y, m] = String(value).split("-");
  const d = new Date(Date.UTC(Number(y), Number(m) - 1, 15));
  if (Number.isNaN(d.getTime())) return String(value);
  return new Intl.DateTimeFormat("tr-TR", { timeZone: TIME_ZONE, month: "long", year: "numeric" }).format(d);
}

/** Saniye → "45 sn önce" / "3 dk önce" */
export function formatAgo(seconds) {
  if (seconds == null || Number.isNaN(Number(seconds))) return "—";
  const s = Math.max(0, Math.round(Number(seconds)));
  if (s < 60) return `${s} sn önce`;
  if (s < 3600) return `${Math.round(s / 60)} dk önce`;
  if (s < 86400) return `${Math.round(s / 3600)} sa önce`;
  return `${Math.round(s / 86400)} gün önce`;
}

/** Bugünse "Bugün 07:34", değilse tam tarih-saat. */
export function formatRelativeDateTime(value) {
  const d = toDate(value);
  if (!d) return "—";
  return isoDayFormatter.format(d) === todayISO() ? `Bugün ${formatClock(d)}` : formatDateTime(d);
}
