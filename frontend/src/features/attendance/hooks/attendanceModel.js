/** Yoklama geçmişi için saf yardımcılar (test edilebilir). Europe/Istanbul sabit UTC+3'tür. */

/** "2026-11-01" → o günün Istanbul başlangıcı (Instant ISO). */
export function startOfDayInstant(isoDate) {
  return new Date(`${isoDate}T00:00:00+03:00`).toISOString();
}

/** "2026-11-19" → o günün Istanbul sonu (Instant ISO). */
export function endOfDayInstant(isoDate) {
  return new Date(`${isoDate}T23:59:59.999+03:00`).toISOString();
}

/** Olaylardan sefer bazlı resmi durum: devamsızlık > iniş > biniş > kayıt yok. */
export function statusFromEvents(events) {
  const types = new Set(events.map((e) => e.eventType));
  if (types.has("ABSENCE")) return "ABSENT";
  if (types.has("DROPOFF")) return "DROPPED_OFF";
  if (types.has("BOARDING")) return "BOARDED";
  return "NONE";
}

/** Olayları sefer bazında gruplar; en yeni sefer önce. */
export function groupByTrip(events) {
  const map = new Map();
  for (const e of events ?? []) {
    if (!map.has(e.tripId)) map.set(e.tripId, []);
    map.get(e.tripId).push(e);
  }
  return [...map.entries()]
    .map(([tripId, list]) => {
      const sorted = [...list].sort((a, b) => String(a.recordedAt).localeCompare(String(b.recordedAt)));
      return { tripId, events: sorted, firstAt: sorted[0]?.recordedAt, status: statusFromEvents(sorted) };
    })
    .sort((a, b) => String(b.firstAt).localeCompare(String(a.firstAt)));
}

/** İnceleme dialogunda seçilebilecek hedef durumlar (mevcut durum hariç — aynısı 400 döner). */
export function reviewTargetOptions(currentStatus) {
  return ["BOARDED", "DROPPED_OFF", "ABSENT", "NONE"].filter((s) => s !== currentStatus);
}
