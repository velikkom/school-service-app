package com.okulservisi.backend.vehicle.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.DriverOnly;
import com.okulservisi.backend.vehicle.api.VehicleApiPaths;
import com.okulservisi.backend.vehicle.dto.request.VehicleCreateRequest;
import com.okulservisi.backend.vehicle.dto.response.VehicleResponse;
import com.okulservisi.backend.vehicle.dto.request.VehicleUpdateRequest;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.mapper.VehicleMapper;
import com.okulservisi.backend.vehicle.service.VehicleService;
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

/**
 * docs/API.md "5. Araç" (bkz. docs/RBAC_MATRIX.md). {@code @PreAuthorize} artık METOD seviyesinde
 * (class-level yerine) — diğer controller'larla (DriverController, TripController, ...) aynı stil.
 *
 * <p>(Faz F takip turu) {@code GET /api/vehicles/mine} artık uygulanıyor — bkz. {@link
 * VehicleService#getActiveVehicleForDriver}; önceki BLOCKED notu (trip tablosunun henüz var olmaması)
 * artık geçerli değil, trip modülü eklendi.
 */
@RestController
@RequestMapping(VehicleApiPaths.BASE)
@RequiredArgsConstructor
public class VehicleController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "plate", "capacity", "createdAt");

    private final VehicleService vehicleService;
    private final VehicleMapper vehicleMapper;

    @GetMapping
    @AdminOnly
    public PageResponse<VehicleResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<Vehicle> result = vehicleService.list(q, isActive, pageable);
        return PageResponse.of(result, result.getContent().stream().map(vehicleMapper::toResponse).toList());
    }

    /** {@code GET /api/vehicles/mine} — SADECE DRIVER, kendi aktif atandığı araç (bkz. VehicleService javadoc'u). */
    @GetMapping(VehicleApiPaths.MINE)
    @DriverOnly
    public ResponseEntity<VehicleResponse> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(vehicleMapper.toResponse(vehicleService.getActiveVehicleForDriver(principal.getUserId())));
    }

    @GetMapping(VehicleApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<VehicleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleMapper.toResponse(vehicleService.getById(id)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleCreateRequest request) {
        return ResponseEntity.ok(vehicleMapper.toResponse(vehicleService.create(request)));
    }

    @PatchMapping(VehicleApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<VehicleResponse> update(@PathVariable Long id, @Valid @RequestBody VehicleUpdateRequest request) {
        return ResponseEntity.ok(vehicleMapper.toResponse(vehicleService.update(id, request)));
    }
}
