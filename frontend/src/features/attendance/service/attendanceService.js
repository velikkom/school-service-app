import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Öğrenci yoklama geçmişi (API §10). */
export const attendanceService = {
  forStudent: (studentId, params) => http.get(`/students/${studentId}/attendance${qs(params)}`),
};
