package com.okulservisi.backend.hostess.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.hostess.api.HostessApiPaths;
import com.okulservisi.backend.hostess.dto.request.HostessCreateRequest;
import com.okulservisi.backend.hostess.dto.response.HostessResponse;
import com.okulservisi.backend.hostess.dto.request.HostessUpdateRequest;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.mapper.HostessMapper;
import com.okulservisi.backend.hostess.service.HostessService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "6. Şoför/Hostes" (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(HostessApiPaths.BASE)
@RequiredArgsConstructor
public class HostessController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt");

    private final HostessService hostessService;
    private final HostessMapper hostessMapper;

    @GetMapping
    @AdminOnly
    public PageResponse<HostessResponse> list(
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<Hostess> result = hostessService.list(isActive, pageable);
        return PageResponse.of(result, result.getContent().stream().map(hostessMapper::toResponse).toList());
    }

    @GetMapping(HostessApiPaths.ME)
    @PreAuthorize("hasRole('HOSTESS')")
    public ResponseEntity<HostessResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(hostessMapper.toResponse(hostessService.getByUserId(principal.getUserId())));
    }

    @GetMapping(HostessApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<HostessResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(hostessMapper.toResponse(hostessService.getById(id)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<HostessResponse> create(@Valid @RequestBody HostessCreateRequest request) {
        return ResponseEntity.ok(hostessMapper.toResponse(hostessService.create(request)));
    }

    @PatchMapping(HostessApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<HostessResponse> update(@PathVariable Long id, @RequestBody HostessUpdateRequest request) {
        return ResponseEntity.ok(hostessMapper.toResponse(hostessService.update(id, request)));
    }
}
