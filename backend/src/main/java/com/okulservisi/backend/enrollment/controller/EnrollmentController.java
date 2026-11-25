package com.okulservisi.backend.enrollment.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.enrollment.api.EnrollmentApiPaths;
import com.okulservisi.backend.enrollment.dto.request.EnrollmentCreateRequest;
import com.okulservisi.backend.enrollment.dto.response.EnrollmentResponse;
import com.okulservisi.backend.enrollment.service.EnrollmentService;
import com.okulservisi.backend.security.annotation.ParentOnly;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md §19 "Veli öz-kayıt" — sadece PARENT, sadece kendi kaydı (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(EnrollmentApiPaths.BASE)
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    @ParentOnly
    public ResponseEntity<EnrollmentResponse> create(@Valid @RequestBody EnrollmentCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.create(request, principal));
    }
}
