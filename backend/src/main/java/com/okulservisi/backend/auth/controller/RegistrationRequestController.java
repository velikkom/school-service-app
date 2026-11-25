package com.okulservisi.backend.auth.controller;

import com.okulservisi.backend.auth.api.AuthApiPaths;
import com.okulservisi.backend.auth.entity.RegistrationRequest;
import com.okulservisi.backend.auth.mapper.RegistrationRequestMapper;
import com.okulservisi.backend.auth.entity.RegistrationRequestStatus;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestApproveRequest;
import com.okulservisi.backend.auth.dto.response.RegistrationRequestApproveResponse;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestRejectRequest;
import com.okulservisi.backend.auth.dto.response.RegistrationRequestResponse;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.auth.service.RegistrationRequestService;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.mapper.UserMapper;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md §18 — tamamı ADMIN-only (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(AuthApiPaths.REGISTRATION_REQUESTS_BASE)
@RequiredArgsConstructor
@AdminOnly
public class RegistrationRequestController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt", "email", "status");

    private final RegistrationRequestService registrationRequestService;
    private final RegistrationRequestMapper registrationRequestMapper;
    private final UserMapper userMapper;

    @GetMapping
    public PageResponse<RegistrationRequestResponse> list(
            @RequestParam(required = false) RegistrationRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "createdAt,desc");
        Page<RegistrationRequest> result = registrationRequestService.list(status, pageable);
        return PageResponse.of(result, result.getContent().stream().map(registrationRequestMapper::toResponse).toList());
    }

    @GetMapping(AuthApiPaths.REGISTRATION_REQUEST_BY_ID)
    public ResponseEntity<RegistrationRequestResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(registrationRequestMapper.toResponse(registrationRequestService.getById(id)));
    }

    @PostMapping(AuthApiPaths.REGISTRATION_REQUEST_APPROVE)
    public ResponseEntity<RegistrationRequestApproveResponse> approve(@PathVariable Long id,
            @Valid @RequestBody RegistrationRequestApproveRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        User user = registrationRequestService.approve(id, request, admin);
        return ResponseEntity.ok(new RegistrationRequestApproveResponse(userMapper.toResponse(user)));
    }

    @PostMapping(AuthApiPaths.REGISTRATION_REQUEST_REJECT)
    public ResponseEntity<RegistrationRequestResponse> reject(@PathVariable Long id,
            @RequestBody(required = false) RegistrationRequestRejectRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        RegistrationRequestRejectRequest effectiveRequest = request == null
                ? new RegistrationRequestRejectRequest(null) : request;
        RegistrationRequest rejected = registrationRequestService.reject(id, effectiveRequest, admin);
        return ResponseEntity.ok(registrationRequestMapper.toResponse(rejected));
    }
}
