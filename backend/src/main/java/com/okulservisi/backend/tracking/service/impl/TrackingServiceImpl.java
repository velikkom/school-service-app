package com.okulservisi.backend.tracking.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.tracking.dto.request.LocationCreateRequest;
import com.okulservisi.backend.tracking.dto.response.LocationResponse;
import com.okulservisi.backend.tracking.entity.VehicleLocation;
import com.okulservisi.backend.tracking.policy.TrackingAccessGuard;
import com.okulservisi.backend.tracking.repository.VehicleLocationRepository;
import com.okulservisi.backend.tracking.service.TrackingService;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.policy.TripAccessGuard;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.user.entity.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** docs/API.md "16. Konum takibi" (bkz. docs/RBAC_MATRIX.md). */
@Service
@RequiredArgsConstructor
public class TrackingServiceImpl implements TrackingService {

    /** docs/API.md "16. Konum takibi": {@code isOffline} örneği ">90s güncel değilse". */
    private static final long OFFLINE_THRESHOLD_SECONDS = 90;

    private final TripRepository tripRepository;
    private final VehicleLocationRepository vehicleLocationRepository;
    private final TripAccessGuard tripAccessGuard;
    private final TrackingAccessGuard trackingAccessGuard;
    private final Clock clock;

    @Override
    @Transactional
    public VehicleLocation recordLocation(Long tripId, LocationCreateRequest request, AuthenticatedUser principal) {
        Trip trip = trackingAccessGuard.assertDriverAssignedTrip(tripId, principal);
        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Sefer IN_PROGRESS durumda değilken konum gönderilemez (mevcut durum: " + trip.getStatus() + ").");
        }

        VehicleLocation location = new VehicleLocation();
        location.setTrip(trip);
        location.setLat(BigDecimal.valueOf(request.lat()));
        location.setLng(BigDecimal.valueOf(request.lng()));
        location.setRecordedAt(request.recordedAt());
        return vehicleLocationRepository.save(location);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationResponse getLatest(Long tripId, AuthenticatedUser principal) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> NotFoundException.of("Sefer", tripId));
        tripAccessGuard.assertReadableForTracking(tripId, principal);
        if (principal.getRole() != Role.ADMIN && trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw NotFoundException.of("Sefer", tripId);
        }

        VehicleLocation location = vehicleLocationRepository.findFirstByTripIdOrderByRecordedAtDesc(tripId)
                .orElseThrow(() -> new NotFoundException("Bu sefer için henüz konum kaydı yok."));

        long staleSeconds = Math.max(0, Duration.between(location.getRecordedAt(), clock.instant()).getSeconds());
        boolean offline = staleSeconds > OFFLINE_THRESHOLD_SECONDS;
        return new LocationResponse(
                location.getLat().doubleValue(),
                location.getLng().doubleValue(),
                location.getRecordedAt(),
                staleSeconds,
                offline);
    }
}
