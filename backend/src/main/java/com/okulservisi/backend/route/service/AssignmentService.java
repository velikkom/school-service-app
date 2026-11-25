package com.okulservisi.backend.route.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.route.dto.request.AssignmentCreateRequest;
import com.okulservisi.backend.route.dto.request.AssignmentUpdateRequest;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * docs/API.md "8. Öğrenci atamaları" — ADMIN create/update; PARENT/STUDENT kendi kapsamında
 * salt-okunur. Çakışma kuralı (docs/DATABASE_DESIGN.md): aynı öğrenci+yön için tarih aralığı çakışan
 * ikinci atama, {@code SERIALIZABLE} işlem içinde gerçek bir overlap sorgusuyla engellenir.
 */
public interface AssignmentService {

    Page<StudentRouteAssignment> list(Long studentId, Long routeId, RouteDirection direction,
            AuthenticatedUser principal, Pageable pageable);

    StudentRouteAssignment getByIdForPrincipal(Long id, AuthenticatedUser principal);

    StudentRouteAssignment create(AssignmentCreateRequest request);

    StudentRouteAssignment update(Long id, AssignmentUpdateRequest request);
}
