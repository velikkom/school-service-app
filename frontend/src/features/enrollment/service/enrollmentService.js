import { http } from "@/lib/http/client";

/** Veli öz-kayıt / ikinci çocuk başvurusu (API §19). */
export const enrollmentService = {
  submit: (body) => http.post("/parents/me/student-enrollments", body),
};
