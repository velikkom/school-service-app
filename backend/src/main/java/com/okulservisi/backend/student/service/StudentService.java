package com.okulservisi.backend.student.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.student.dto.request.StudentCreateRequest;
import com.okulservisi.backend.student.dto.request.StudentUpdateRequest;
import com.okulservisi.backend.student.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * docs/API.md "3. Öğrenci" — ADMIN tam erişim; PARENT sadece aktif bağlı çocukları; STUDENT sadece
 * kendisi (bkz. docs/RBAC_MATRIX.md "Nesne bazlı erişim ilkesi").
 */
public interface StudentService {

    Page<Student> list(String q, Long routeId, Boolean isActive, AuthenticatedUser principal, Pageable pageable);

    Student getByIdForPrincipal(Long id, AuthenticatedUser principal);

    Student create(StudentCreateRequest request);

    Student update(Long id, StudentUpdateRequest request);

    Student deactivate(Long id);

    /**
     * Sprint 3 — STUDENT hesabını öğrenci kaydına bağlar (bkz. docs/API.md §3 "Öğrenci hesabı bağı").
     * Aynı çift zaten bağlıysa değişiklik yapmadan döner (idempotent); çakışan bağlarda 409.
     */
    Student linkAccount(Long studentId, Long userId, Long actorUserId);

    /** Sprint 3 — öğrencinin hesap bağını kaldırır; bağ yoksa değişiklik yapmadan döner (idempotent). */
    Student unlinkAccount(Long studentId, Long actorUserId);
}
