package com.okulservisi.backend.driver.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.driver.api.DriverApiPaths;
import com.okulservisi.backend.driver.dto.request.DriverCreateRequest;
import com.okulservisi.backend.driver.dto.response.DriverResponse;
import com.okulservisi.backend.driver.dto.request.DriverUpdateRequest;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.mapper.DriverMapper;
import com.okulservisi.backend.driver.service.DriverService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.DriverOnly;
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

/** docs/API.md "6. Şoför/Hostes" (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(DriverApiPaths.BASE)
@RequiredArgsConstructor
public class DriverController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "licenseNo", "createdAt");

    private final DriverService driverService;
    private final DriverMapper driverMapper;

    @GetMapping
    @AdminOnly
    public PageResponse<DriverResponse> list(
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<Driver> result = driverService.list(isActive, pageable);
        return PageResponse.of(result, result.getContent().stream().map(driverMapper::toResponse).toList());
    }

    @GetMapping(DriverApiPaths.ME)
    @DriverOnly
    public ResponseEntity<DriverResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(driverMapper.toResponse(driverService.getByUserId(principal.getUserId())));
    }

    @GetMapping(DriverApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<DriverResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(driverMapper.toResponse(driverService.getById(id)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody DriverCreateRequest request) {
        return ResponseEntity.ok(driverMapper.toResponse(driverService.create(request)));
    }

    @PatchMapping(DriverApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<DriverResponse> update(@PathVariable Long id, @RequestBody DriverUpdateRequest request) {
        return ResponseEntity.ok(driverMapper.toResponse(driverService.update(id, request)));
    }
}
