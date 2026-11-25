package com.okulservisi.backend.route.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.route.dto.request.StopChangeDecisionRequest;
import com.okulservisi.backend.route.dto.request.StopChangeRequestCreateRequest;
import com.okulservisi.backend.route.dto.response.StopChangeRequestResponse;
import com.okulservisi.backend.route.dto.response.StopOptionResponse;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StopChangeRequestStatus;
import com.okulservisi.backend.route.service.StopChangeRequestService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AdminOrParent;
import com.okulservisi.backend.security.annotation.ParentOnly;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md §25 — {@code /api/stop-change-requests}. */
@RestController
@RequestMapping("/api/stop-change-requests")
@RequiredArgsConstructor
public class StopChangeRequestController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt", "status", "effectiveDate");

    private final StopChangeRequestService service;

    @GetMapping("/options")
    @AdminOrParent
    public List<StopOptionResponse> options(@RequestParam Long studentId, @RequestParam RouteDirection direction,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.options(studentId, direction, principal);
    }

    @PostMapping
    @ParentOnly
    public ResponseEntity<StopChangeRequestResponse> create(@Valid @RequestBody StopChangeRequestCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, principal));
    }

    @GetMapping
    @AdminOrParent
    public PageResponse<StopChangeRequestResponse> list(
            @RequestParam(required = false) StopChangeRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Page<StopChangeRequestResponse> result = service.list(status, principal,
                SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "createdAt,desc"));
        return PageResponse.of(result, result.getContent());
    }

    @PostMapping("/{id}/approve")
    @AdminOnly
    public StopChangeRequestResponse approve(@PathVariable Long id,
            @Valid @RequestBody(required = false) StopChangeDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.approve(id, request == null ? null : request.note(), principal);
    }

    @PostMapping("/{id}/reject")
    @AdminOnly
    public StopChangeRequestResponse reject(@PathVariable Long id, @Valid @RequestBody StopChangeDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.reject(id, request.reason(), principal);
    }
}
