package com.okulservisi.backend.route.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.route.api.RouteApiPaths;
import com.okulservisi.backend.route.dto.request.AssignmentCreateRequest;
import com.okulservisi.backend.route.dto.response.AssignmentResponse;
import com.okulservisi.backend.route.dto.request.AssignmentUpdateRequest;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.route.mapper.AssignmentMapper;
import com.okulservisi.backend.route.service.AssignmentService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AdminParentOrStudent;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "8. Öğrenci atamaları" (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(RouteApiPaths.ASSIGNMENT_BASE)
@RequiredArgsConstructor
public class AssignmentController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "validFrom", "validTo", "createdAt");

    private final AssignmentService assignmentService;
    private final AssignmentMapper assignmentMapper;

    @GetMapping
    @AdminParentOrStudent
    public PageResponse<AssignmentResponse> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) RouteDirection direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<StudentRouteAssignment> result = assignmentService.list(studentId, routeId, direction, principal, pageable);
        return PageResponse.of(result, result.getContent().stream().map(assignmentMapper::toResponse).toList());
    }

    @GetMapping(RouteApiPaths.ASSIGNMENT_BY_ID)
    @AdminParentOrStudent
    public ResponseEntity<AssignmentResponse> getById(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(assignmentMapper.toResponse(assignmentService.getByIdForPrincipal(id, principal)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<AssignmentResponse> create(@Valid @RequestBody AssignmentCreateRequest request) {
        return ResponseEntity.ok(assignmentMapper.toResponse(assignmentService.create(request)));
    }

    @PatchMapping(RouteApiPaths.ASSIGNMENT_BY_ID)
    @AdminOnly
    public ResponseEntity<AssignmentResponse> update(@PathVariable Long id, @RequestBody AssignmentUpdateRequest request) {
        return ResponseEntity.ok(assignmentMapper.toResponse(assignmentService.update(id, request)));
    }
}
