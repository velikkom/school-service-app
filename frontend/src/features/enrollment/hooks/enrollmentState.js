/**
 * PARENT öz-kayıt kapısı durumu (saf fonksiyon). Ayrı bir durum ucu yoktur; `GET /students` +
 * öğrenci başına `GET /contracts?studentId=` sonucu birleştirilir.
 *
 *  - "WIZARD":  hiç öğrenci yok, ya da bütün öğrenciler iptal (pasif öğrenci veya sözleşmelerinin
 *               tamamı CANCELLED) → kabuk dışında tam ekran sihirbaz.
 *  - "OPEN":    en az bir ACTIVE sözleşme → uygulama açık (bekleyen ikinci başvuru kilitlemez).
 *  - "PENDING": aktif sözleşme yok ama PENDING_APPROVAL var → "Onay bekliyor" ekranı.
 *  - Diğer (ör. sözleşmesi olmayan / süresi dolmuş aktif öğrenci — yönetici tarafından eklenmiş) → "OPEN";
 *    veli verisini görmeye devam eder, kapı kilitlemez.
 *
 * @param {Array<{id:number,isActive?:boolean,fullName?:string}>} students
 * @param {Record<number, Array<{status:string}>>} contractsByStudent
 */
export function deriveEnrollmentState(students, contractsByStudent = {}) {
  const list = students ?? [];
  if (list.length === 0) return { state: "WIZARD", pendingStudent: null };

  let pendingStudent = null;
  let allCancelled = true;
  for (const s of list) {
    const contracts = contractsByStudent[s.id] ?? [];
    if (contracts.some((c) => c.status === "ACTIVE")) return { state: "OPEN", pendingStudent: null };
    if (!pendingStudent && contracts.some((c) => c.status === "PENDING_APPROVAL")) pendingStudent = s;
    const cancelled = s.isActive === false || (contracts.length > 0 && contracts.every((c) => c.status === "CANCELLED"));
    if (!cancelled) allCancelled = false;
  }
  if (pendingStudent) return { state: "PENDING", pendingStudent };
  if (allCancelled) return { state: "WIZARD", pendingStudent: null };
  return { state: "OPEN", pendingStudent: null };
}
