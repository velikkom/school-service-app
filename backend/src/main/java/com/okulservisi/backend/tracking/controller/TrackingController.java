package com.okulservisi.backend.tracking.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.security.annotation.AnyRole;
import com.okulservisi.backend.security.annotation.DriverOnly;
import com.okulservisi.backend.tracking.api.TrackingApiPaths;
import com.okulservisi.backend.tracking.dto.response.LocationAckResponse;
import com.okulservisi.backend.tracking.dto.request.LocationCreateRequest;
import com.okulservisi.backend.tracking.dto.response.LocationResponse;
import com.okulservisi.backend.tracking.entity.VehicleLocation;
import com.okulservisi.backend.tracking.service.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md "16. Konum takibi" (bkz. docs/RBAC_MATRIX.md). POST SADECE DRIVER'a açık (HOSTESS bu
 * uçnoktayı hiç çağıramaz — rol bazlı kaba kural burada 403 ile reddeder; nesne bazlı "bu DRIVER bu
 * sefere atanmış mı" kontrolü {@link TrackingAccessGuard} ile 404 döner).
 */
@RestController
@RequestMapping(TrackingApiPaths.BASE)
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @PostMapping(TrackingApiPaths.LOCATIONS)
    @DriverOnly
    public ResponseEntity<LocationAckResponse> recordLocation(@PathVariable Long tripId,
            @Valid @RequestBody LocationCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        VehicleLocation saved = trackingService.recordLocation(tripId, request, principal);
        LocationAckResponse response = new LocationAckResponse(
                saved.getId(), saved.getLat().doubleValue(), saved.getLng().doubleValue(), saved.getRecordedAt());
        return ResponseEntity.ok(response);
    }

    @GetMapping(TrackingApiPaths.LATEST)
    @AnyRole
    public ResponseEntity<LocationResponse> latest(@PathVariable Long tripId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(trackingService.getLatest(tripId, principal));
    }
}
