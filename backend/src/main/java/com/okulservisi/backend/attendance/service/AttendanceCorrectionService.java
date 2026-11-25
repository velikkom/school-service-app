package com.okulservisi.backend.attendance.service;

import com.okulservisi.backend.attendance.dto.request.AttendanceCorrectionApproveRequest;
import com.okulservisi.backend.attendance.dto.request.AttendanceCorrectionCreateRequest;
import com.okulservisi.backend.attendance.dto.response.AttendanceCorrectionResponse;
import com.okulservisi.backend.attendance.entity.CorrectionStatus;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** docs/API.md §24 — yoklama düzeltme / inceleme talepleri. */
public interface AttendanceCorrectionService {

    AttendanceCorrectionResponse create(AttendanceCorrectionCreateRequest request, AuthenticatedUser principal);

    Page<AttendanceCorrectionResponse> list(CorrectionStatus status, Long tripId, AuthenticatedUser principal,
            Pageable pageable);

    AttendanceCorrectionResponse get(Long id, AuthenticatedUser principal);

    AttendanceCorrectionResponse approve(Long id, AttendanceCorrectionApproveRequest request,
            AuthenticatedUser principal);

    AttendanceCorrectionResponse reject(Long id, String reason, AuthenticatedUser principal);
}
