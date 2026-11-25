package com.okulservisi.backend.enrollment.dto.response;

import com.okulservisi.backend.contract.dto.response.ContractResponse;
import com.okulservisi.backend.student.dto.response.StudentResponse;

/** docs/API.md §19 — {@code POST /api/parents/me/student-enrollments} başarılı yanıtı ({@code 201}). */
public record EnrollmentResponse(StudentResponse student, ContractResponse contract) {
}
