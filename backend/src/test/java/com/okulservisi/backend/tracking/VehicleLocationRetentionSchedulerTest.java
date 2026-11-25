package com.okulservisi.backend.tracking;
import com.okulservisi.backend.tracking.entity.VehicleLocation;
import com.okulservisi.backend.tracking.repository.VehicleLocationRepository;
import com.okulservisi.backend.tracking.service.VehicleLocationRetentionScheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.repository.RouteRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/DATABASE_DESIGN.md "vehicle_location ... Saklama: 90 gün" kuralının gerçek Postgres'e
 * karşı doğrulanması. Scheduler, {@code @Transactional}'ın etkili olması için (AOP proxy gerektirir)
 * Spring context'ten @Autowired edilir — gerçek sistem saatine göre offsetlenmiş kayıtlarla test edilir.
 */
class VehicleLocationRetentionSchedulerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private VehicleLocationRepository vehicleLocationRepository;

    @Autowired
    private VehicleLocationRetentionScheduler retentionScheduler;

    private Trip createTrip() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Driver driver = new Driver();
        driver.setUser(user);
        driver.setLicenseNo("LIC-" + UUID.randomUUID());
        driver.setActive(true);
        driver = driverRepository.save(driver);

        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("TRK-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        vehicle = vehicleRepository.save(vehicle);

        Route route = new Route();
        route.setName("Retention Route " + UUID.randomUUID());
        route.setDirection(RouteDirection.MORNING);
        route.setActive(true);
        route = routeRepository.save(route);

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setBusinessDate(LocalDate.of(2026, 5, 1));
        trip.setDirection(RouteDirection.MORNING);
        trip.setStatus(TripStatus.COMPLETED);
        return tripRepository.save(trip);
    }

    private VehicleLocation insertLocation(Trip trip, Instant recordedAt) {
        VehicleLocation location = new VehicleLocation();
        location.setTrip(trip);
        location.setLat(new BigDecimal("40.123456"));
        location.setLng(new BigDecimal("29.654321"));
        location.setRecordedAt(recordedAt);
        return vehicleLocationRepository.save(location);
    }

    @Test
    void purgeOldLocations_deletesOnlyRecordsOlderThan90Days() {
        Instant now = Instant.now();
        Trip trip = createTrip();
        VehicleLocation old91Days = insertLocation(trip, now.minus(91, ChronoUnit.DAYS));
        VehicleLocation recentWithin90Days = insertLocation(trip, now.minus(89, ChronoUnit.DAYS));
        VehicleLocation recent = insertLocation(trip, now.minusSeconds(60));

        retentionScheduler.purgeOldLocations();

        List<VehicleLocation> remaining = vehicleLocationRepository.findAllById(
                List.of(old91Days.getId(), recentWithin90Days.getId(), recent.getId()));

        assertThat(remaining)
                .as("sadece 90 günden ESKİ (91 gün) kayıt silinmeli; 89 gün ve daha yeni kalmalı")
                .extracting(VehicleLocation::getId)
                .containsExactlyInAnyOrder(recentWithin90Days.getId(), recent.getId());
    }

    @Test
    void purgeOldLocations_whenNothingExpired_deletesNothing() {
        Instant now = Instant.now();
        Trip trip = createTrip();
        VehicleLocation recent = insertLocation(trip, now.minusSeconds(5));

        retentionScheduler.purgeOldLocations();

        assertThat(vehicleLocationRepository.findById(recent.getId())).isPresent();
    }
}
