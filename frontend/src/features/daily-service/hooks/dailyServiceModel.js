/**
 * Günlük servis kartı için saf yardımcılar (test edilebilir).
 */

/** Ana kart: yoldaki sefer, yoksa planlı, yoksa günün son seferi. */
export function pickHero(entries) {
  if (!entries?.length) return null;
  return entries.find((e) => e.status === "IN_PROGRESS") ?? entries.find((e) => e.status === "PLANNED") ?? entries[entries.length - 1];
}

export function isOpenTrip(entry) {
  return entry?.status === "PLANNED" || entry?.status === "IN_PROGRESS";
}

/** Sefer açıkken gösterilecek gecikme (yalnız DELAY + dakika). */
export function delayMinutesOf(entry) {
  const inc = entry?.latestIncident;
  if (!inc || inc.type !== "DELAY" || !isOpenTrip(entry)) return null;
  return inc.delayMinutes ?? null;
}

/**
 * Rol + API bayraklarına göre veli aksiyonları. STUDENT/ADMIN için bayraklar backend'de zaten false'tur;
 * ayrıca rol kontrolü yapılır (STUDENT'ta buton hiç çizilmez).
 */
export function noticeActionsFor(entry, role) {
  if (role !== "PARENT" || !entry) return { canSubmit: false, canWithdraw: false, lockedByAttendance: false };
  const canSubmit = entry.canSubmitAbsenceNotice === true;
  const canWithdraw = entry.canWithdrawAbsenceNotice === true && Boolean(entry.absenceNotice?.id);
  const lockedByAttendance = !canSubmit && !canWithdraw && isOpenTrip(entry) && entry.attendanceStatus && entry.attendanceStatus !== "NONE";
  return { canSubmit, canWithdraw, lockedByAttendance: Boolean(lockedByAttendance) };
}
