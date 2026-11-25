package com.okulservisi.backend.tracking.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.tracking.dto.request.LocationCreateRequest;
import com.okulservisi.backend.tracking.dto.response.LocationResponse;
import com.okulservisi.backend.tracking.entity.VehicleLocation;

/** docs/API.md "16. Konum takibi" (bkz. docs/RBAC_MATRIX.md). */
public interface TrackingService {

    /** {@code POST /api/tracking/trips/{tripId}/locations} — SADECE o sefere atanmış DRIVER. */
    VehicleLocation recordLocation(Long tripId, LocationCreateRequest request, AuthenticatedUser principal);

    /**
     * {@code GET /api/tracking/trips/{tripId}/latest} — ADMIN tümü (sefer durumu fark etmez);
     * DRIVER/HOSTESS/PARENT/STUDENT SADECE kendi/ilişkili VE {@code IN_PROGRESS} durumundaki sefer.
     */
    LocationResponse getLatest(Long tripId, AuthenticatedUser principal);
}
