package com.okulservisi.backend.enrollment.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.enrollment.dto.request.EnrollmentCreateRequest;
import com.okulservisi.backend.enrollment.dto.response.EnrollmentResponse;

/**
 * docs/API.md §19 "Veli öz-kayıt (öğrenci kaydı sihirbazı)" — PARENT hesabı hiç bağlı öğrencisi
 * yoksa kendi başına İLK öğrencisini kaydeder.
 */
public interface EnrollmentService {

    EnrollmentResponse create(EnrollmentCreateRequest request, AuthenticatedUser principal);
}
