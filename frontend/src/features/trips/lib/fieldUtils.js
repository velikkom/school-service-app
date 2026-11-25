/**
 * Saha ve sefer ekranlarının saf yardımcıları (React'tan bağımsız, birim testli).
 * API davranışı: docs/design/SPRINT2_API_REFERENCE.md §4.10, §4.17, §10.
 */

export const ROSTER_STATUSES = ["NONE", "BOARDED", "DROPPED_OFF", "ABSENT"];
export const OPEN_TRIP_STATUSES = ["PLANNED", "IN_PROGRESS"];

const DIRECTION_ORDER = { MORNING: 0, AFTERNOON: 1 };

export const isTripOpen = (trip) => Boolean(trip) && OPEN_TRIP_STATUSES.includes(trip.status);

/**
 * `trips/mine` sıralaması string'e göre (AFTERNOON < MORNING) gelir; istemcide tarih → yön (sabah önce)
 * → planlanan saat → id sırasına çevrilir.
 */
export function sortTrips(trips) {
  return [...(trips ?? [])].sort((a, b) => {
    const d = String(a.businessDate ?? "").localeCompare(String(b.businessDate ?? ""));
    if (d) return d;
    const dir = (DIRECTION_ORDER[a.direction] ?? 9) - (DIRECTION_ORDER[b.direction] ?? 9);
    if (dir) return dir;
    const t = String(a.plannedStartTime ?? "99").localeCompare(String(b.plannedStartTime ?? "99"));
    if (t) return t;
    return (a.id ?? 0) - (b.id ?? 0);
  });
}

/** Bugünün aktif seferi: önce yoldaki (IN_PROGRESS), yoksa sıradaki ilk planlı sefer. */
export function pickActiveTrip(trips) {
  const sorted = sortTrips(trips);
  return sorted.find((t) => t.status === "IN_PROGRESS") ?? sorted.find((t) => t.status === "PLANNED") ?? null;
}

/** Roster'ı durak bazında gruplar; durak sırası `sequenceNo` artan, grup içi sıra korunur. */
export function groupRosterByStop(rows) {
  const map = new Map();
  for (const r of rows ?? []) {
    const key = r.stopId ?? `seq-${r.sequenceNo}`;
    if (!map.has(key)) {
      map.set(key, {
        stopId: r.stopId,
        stopName: r.stopName,
        sequenceNo: r.sequenceNo,
        plannedTime: r.stopPlannedTime,
        rows: [],
      });
    }
    map.get(key).rows.push(r);
  }
  return [...map.values()].sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0));
}

/** Durum başına sayaç: { NONE, BOARDED, DROPPED_OFF, ABSENT, total, notices }. */
export function rosterCounts(rows) {
  const c = { NONE: 0, BOARDED: 0, DROPPED_OFF: 0, ABSENT: 0, total: 0, notices: 0 };
  for (const r of rows ?? []) {
    c.total += 1;
    if (c[r.status] !== undefined) c[r.status] += 1;
    if (r.absenceNotice) c.notices += 1;
  }
  return c;
}

/**
 * Sıradaki durak: işlem bekleyen (NONE) ve veli bildirimi olmayan öğrencisi bulunan ilk durak.
 * Yalnız yoldaki seferde anlamlıdır.
 */
export function nextStopId(groups) {
  const g = (groups ?? []).find((x) => x.rows.some((r) => r.status === "NONE" && !r.absenceNotice));
  return g ? g.stopId : null;
}

/**
 * Bir yolcu satırında gösterilecek yoklama aksiyonları. Backend sefer durumunu kontrol etmediği için
 * (API ref F-9) butonlar yalnız IN_PROGRESS seferde verilir. Veli bildirimi "Bindi"yi engellemez.
 */
export function attendanceActions(row, tripStatus) {
  if (tripStatus !== "IN_PROGRESS" || !row) return [];
  if (row.status === "NONE") return ["BOARDING", "ABSENCE"];
  if (row.status === "BOARDED") return ["DROPOFF"];
  return [];
}

/** Düzeltme talebi yalnız kayıtlı satırda ve bekleyen talep yokken açılabilir. */
export function canRequestCorrection(row) {
  return Boolean(row) && row.status !== "NONE" && !row.pendingCorrectionId;
}

/** Düzeltme hedef seçenekleri: mevcut durum hariç (aynı durum 400 döner). */
export function correctionTargets(current) {
  return ROSTER_STATUSES.filter((s) => s !== current);
}

/**
 * Durak zaman çizelgesi için durum: geçilen (done), sıradaki (current) duraklar. Durak koordinatı
 * olmadığından yoklamadan türetilir: tüm öğrencileri işlenmiş duraklar "geçildi" sayılır.
 */
export function stopProgress(stops, groups, tripStatus) {
  const byStop = new Map((groups ?? []).map((g) => [g.stopId, g]));
  const sorted = [...(stops ?? [])].sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0));
  const next = tripStatus === "IN_PROGRESS" ? nextStopId(groups) : null;
  return sorted.map((s) => {
    const g = byStop.get(s.id);
    const count = g ? g.rows.length : 0;
    let state = "todo";
    if (tripStatus === "COMPLETED") state = "done";
    else if (tripStatus === "IN_PROGRESS") {
      if (s.id === next) state = "current";
      else if (g && g.rows.every((r) => r.status !== "NONE" || r.absenceNotice)) state = "done";
    }
    return { ...s, count, state };
  });
}

/** Profil listesi (driver/hostess) + kullanıcı listesi → seçim seçenekleri `{ value: profilId, label: ad }`. */
export function staffOptions(profiles, users) {
  const names = new Map((users ?? []).map((u) => [u.id, u.fullName]));
  return (profiles ?? [])
    .map((p) => ({ value: p.id, label: names.get(p.userId) ?? `Kullanıcı #${p.userId}` }))
    .sort((a, b) => a.label.localeCompare(b.label, "tr"));
}

/** PATCH /trips/{id} gövdesi: yalnız değişen alanlar; hostes kaldırma `removeHostess`. */
export function buildTripPatch(trip, values) {
  const body = {};
  const time = values.plannedStartTime || null;
  const current = trip.plannedStartTime ? String(trip.plannedStartTime).slice(0, 5) : null;
  if (time && time !== current) body.plannedStartTime = time;
  if (values.vehicleId && Number(values.vehicleId) !== trip.vehicleId) body.vehicleId = Number(values.vehicleId);
  if (values.driverId && Number(values.driverId) !== trip.driverId) body.driverId = Number(values.driverId);
  const hostess = values.hostessId ? Number(values.hostessId) : null;
  if (hostess && hostess !== trip.hostessId) body.hostessId = hostess;
  if (!hostess && trip.hostessId) body.removeHostess = true;
  return body;
}

const hhmm = (v) => (v ? String(v).slice(0, 5) : "--:--");

/** Bugünkü/yarınki seferlerden benzersiz güzergâhlar (ilk görülen seferin bilgisiyle). */
export function routesFromTrips(trips, today) {
  const map = new Map();
  for (const t of sortTrips(trips)) {
    if (!t.routeId) continue;
    const entry = map.get(t.routeId) ?? { routeId: t.routeId, routeName: t.routeName, direction: t.direction, when: [] };
    entry.when.push(`${t.businessDate === today ? "Bugün" : "Yarın"} ${hhmm(t.plannedStartTime)}`);
    map.set(t.routeId, entry);
  }
  return [...map.values()];
}
