package com.okulservisi.backend.student.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AdminParentOrStudent;
import com.okulservisi.backend.student.api.StudentApiPaths;
import com.okulservisi.backend.student.dto.request.StudentAccountLinkRequest;
import com.okulservisi.backend.student.dto.request.StudentCreateRequest;
import com.okulservisi.backend.student.dto.response.StudentResponse;
import com.okulservisi.backend.student.dto.request.StudentUpdateRequest;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.mapper.StudentMapper;
import com.okulservisi.backend.student.service.StudentService;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "3. Öğrenci" (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(StudentApiPaths.BASE)
@RequiredArgsConstructor
public class StudentController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "fullName", "birthDate", "grade", "createdAt");

    private final StudentService studentService;
    private final StudentMapper studentMapper;

    @GetMapping
    @AdminParentOrStudent
    public PageResponse<StudentResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long routeId,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<Student> result = studentService.list(q, routeId, isActive, principal, pageable);
        return PageResponse.of(result, result.getContent().stream().map(studentMapper::toResponse).toList());
    }

    @GetMapping(StudentApiPaths.BY_ID)
    @AdminParentOrStudent
    public ResponseEntity<StudentResponse> getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(studentMapper.toResponse(studentService.getByIdForPrincipal(id, principal)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentCreateRequest request) {
        return ResponseEntity.ok(studentMapper.toResponse(studentService.create(request)));
    }

    @PatchMapping(StudentApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<StudentResponse> update(@PathVariable Long id, @Valid @RequestBody StudentUpdateRequest request) {
        return ResponseEntity.ok(studentMapper.toResponse(studentService.update(id, request)));
    }

    /** Sprint 3 — bkz. docs/API.md §3 "Öğrenci hesabı bağı". */
    @PutMapping(StudentApiPaths.ACCOUNT)
    @AdminOnly
    public ResponseEntity<StudentResponse> linkAccount(@PathVariable Long id,
            @Valid @RequestBody StudentAccountLinkRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(studentMapper.toResponse(
                studentService.linkAccount(id, request.userId(), principal.getUserId())));
    }

    @DeleteMapping(StudentApiPaths.ACCOUNT)
    @AdminOnly
    public ResponseEntity<StudentResponse> unlinkAccount(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(studentMapper.toResponse(studentService.unlinkAccount(id, principal.getUserId())));
    }

    @PostMapping(StudentApiPaths.DEACTIVATE)
    @AdminOnly
    public ResponseEntity<StudentResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(studentMapper.toResponse(studentService.deactivate(id)));
    }
}
