import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Öğrencinin günlük servisi ve veli "servise binmeyecek" bildirimi (API §22, §26). */
export const dailyServiceService = {
  forStudent: (studentId, date) => http.get(`/students/${studentId}/daily-service${qs({ date })}`),
  submitAbsenceNotice: (tripId, studentId, note) => http.post(`/trips/${tripId}/absence-notices`, { studentId, note: note || undefined }),
  withdrawAbsenceNotice: (tripId, noticeId) => http.post(`/trips/${tripId}/absence-notices/${noticeId}/withdraw`),
};
