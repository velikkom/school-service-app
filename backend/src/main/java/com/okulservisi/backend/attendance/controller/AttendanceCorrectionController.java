package com.okulservisi.backend.attendance.controller;

import com.okulservisi.backend.attendance.dto.request.AttendanceCorrectionApproveRequest;
import com.okulservisi.backend.attendance.dto.request.AttendanceCorrectionCreateRequest;
import com.okulservisi.backend.attendance.dto.request.DecisionReasonRequest;
import com.okulservisi.backend.attendance.dto.response.AttendanceCorrectionResponse;
import com.okulservisi.backend.attendance.entity.CorrectionStatus;
import com.okulservisi.backend.attendance.service.AttendanceCorrectionService;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.security.annotation.AdminOnly;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md §24 — {@code /api/attendance-corrections}. Açma: DRIVER/HOSTESS (atanmış sefer) ve
 * PARENT (bağlı çocuk); takip: ADMIN tümü, diğerleri kendi açtıkları; sonuçlandırma: SADECE ADMIN.
 * STUDENT erişemez (403).
 */
@RestController
@RequestMapping("/api/attendance-corrections")
@RequiredArgsConstructor
public class AttendanceCorrectionController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt", "status");

    private final AttendanceCorrectionService correctionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DRIVER','HOSTESS','PARENT')")
    public ResponseEntity<AttendanceCorrectionResponse> create(
            @Valid @RequestBody AttendanceCorrectionCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(correctionService.create(request, principal));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER','HOSTESS','PARENT')")
    public PageResponse<AttendanceCorrectionResponse> list(
            @RequestParam(required = false) CorrectionStatus status,
            @RequestParam(required = false) Long tripId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Page<AttendanceCorrectionResponse> result = correctionService.list(status, tripId, principal,
                SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "createdAt,desc"));
        return PageResponse.of(result, result.getContent());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER','HOSTESS','PARENT')")
    public AttendanceCorrectionResponse get(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return correctionService.get(id, principal);
    }

    @PostMapping("/{id}/approve")
    @AdminOnly
    public AttendanceCorrectionResponse approve(@PathVariable Long id,
            @Valid @RequestBody(required = false) AttendanceCorrectionApproveRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        AttendanceCorrectionApproveRequest body = request == null ? new AttendanceCorrectionApproveRequest(null, null)
                : request;
        return correctionService.approve(id, body, principal);
    }

    @PostMapping("/{id}/reject")
    @AdminOnly
    public AttendanceCorrectionResponse reject(@PathVariable Long id, @Valid @RequestBody DecisionReasonRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return correctionService.reject(id, request.reason(), principal);
    }
}
