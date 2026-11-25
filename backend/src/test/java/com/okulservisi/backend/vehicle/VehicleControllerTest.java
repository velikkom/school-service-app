package com.okulservisi.backend.vehicle;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.repository.VehicleRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
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
import com.okulservisi.backend.vehicle.dto.request.VehicleCreateRequest;
import com.okulservisi.backend.vehicle.dto.response.VehicleResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "5. Araç" — plaka normalize+unique, capacity zorunlu pozitif. docs/TEST_PLAN.md
 * eşleştirmesi: T-33, T-34. T-35 ({@code GET /api/vehicles/mine}, SADECE DRIVER) Faz F takip
 * turunda (trip modülü artık var) uygulandı — bkz. {@code mine_*} testleri altta ve {@link
 * VehicleService#getActiveVehicleForDriver} javadoc'u ("aktif atandığı araç" tanım kararı).
 */
class VehicleControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private Driver createDriver() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Driver driver = new Driver();
        driver.setUser(user);
        driver.setLicenseNo("LIC-" + UUID.randomUUID());
        driver.setActive(true);
        return driverRepository.save(driver);
    }

    private Route createRoute(RouteDirection direction) {
        Route route = new Route();
        route.setName("Route " + UUID.randomUUID());
        route.setDirection(direction);
        route.setActive(true);
        return routeRepository.save(route);
    }

    private Vehicle createVehicle(String suffix) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("MINE-" + suffix);
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        return vehicleRepository.save(vehicle);
    }

    private Trip createTrip(Route route, Vehicle vehicle, Driver driver, LocalDate businessDate, TripStatus status) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setBusinessDate(businessDate);
        trip.setDirection(route.getDirection());
        trip.setStatus(status);
        return tripRepository.save(trip);
    }

    // ---- T-33 ----
    @Test
    void createVehicle_withDuplicatePlateDifferentFormatting_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        String suffix = String.valueOf(System.nanoTime() % 1_000_000L);
        VehicleCreateRequest first = new VehicleCreateRequest("34 AB " + suffix, 20, "Mercedes");
        ResponseEntity<VehicleResponse> firstResponse = restTemplate().exchange(
                "/api/vehicles", HttpMethod.POST, new HttpEntity<>(first, authHeaderFor(admin)), VehicleResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody().plate()).isEqualTo("34AB" + suffix);

        VehicleCreateRequest duplicateDifferentFormatting = new VehicleCreateRequest("34ab" + suffix, 16, "Ford");
        ResponseEntity<ApiError> duplicateResponse = restTemplate().exchange(
                "/api/vehicles", HttpMethod.POST, new HttpEntity<>(duplicateDifferentFormatting, authHeaderFor(admin)), ApiError.class);

        assertThat(duplicateResponse.getStatusCode())
                .as("plaka normalize edilip (büyük harf/boşluksuz) aynı araç tekrar oluşturulamamalı")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T-34 ----
    @Test
    void createVehicle_withNonPositiveCapacity_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        VehicleCreateRequest zeroCapacity = new VehicleCreateRequest("06 XY " + UUID.randomUUID().toString().substring(0, 4), 0, null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/vehicles", HttpMethod.POST, new HttpEntity<>(zeroCapacity, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void vehicleEndpoints_asDriver_return403() {
        User driver = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/vehicles", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver)), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- T-35 ----
    @Test
    void mine_asDriverWithActiveTrip_returnsAssignedVehicle() {
        Driver driver = createDriver();
        Route route = createRoute(RouteDirection.MORNING);
        Vehicle vehicle = createVehicle(UUID.randomUUID().toString().substring(0, 8));
        createTrip(route, vehicle, driver, LocalDate.of(2026, 8, 1), TripStatus.PLANNED);

        ResponseEntity<VehicleResponse> response = restTemplate().exchange(
                "/api/vehicles/mine", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver.getUser())), VehicleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(vehicle.getId());
    }

    @Test
    void mine_picksMostRecentNonCancelledTrip_overCancelledOrOlderOnes() {
        Driver driver = createDriver();
        Route route = createRoute(RouteDirection.MORNING);
        Vehicle olderVehicle = createVehicle(UUID.randomUUID().toString().substring(0, 8));
        Vehicle cancelledVehicle = createVehicle(UUID.randomUUID().toString().substring(0, 8));
        Vehicle mostRecentVehicle = createVehicle(UUID.randomUUID().toString().substring(0, 8));

        createTrip(route, olderVehicle, driver, LocalDate.of(2026, 8, 1), TripStatus.COMPLETED);
        createTrip(route, cancelledVehicle, driver, LocalDate.of(2026, 8, 10), TripStatus.CANCELLED);
        createTrip(route, mostRecentVehicle, driver, LocalDate.of(2026, 8, 5), TripStatus.PLANNED);

        ResponseEntity<VehicleResponse> response = restTemplate().exchange(
                "/api/vehicles/mine", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver.getUser())), VehicleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id())
                .as("en güncel businessDate'e sahip İPTAL EDİLMEMİŞ sefer (2026-08-05, CANCELLED olan 08-10'dan sonra seçilmemeli)")
                .isEqualTo(mostRecentVehicle.getId());
    }

    @Test
    void mine_driverWithNoTripsAtAll_returns404() {
        Driver driver = createDriver();

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/vehicles/mine", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver.getUser())), ApiError.class);

        assertThat(response.getStatusCode())
                .as("hiç seferi olmayan DRIVER için 404 (boş 200 DEĞİL — bkz. VehicleService javadoc karar gerekçesi)")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mine_driverWithOnlyCancelledTrip_returns404() {
        Driver driver = createDriver();
        Route route = createRoute(RouteDirection.MORNING);
        createTrip(route, createVehicle(UUID.randomUUID().toString().substring(0, 8)), driver, LocalDate.of(2026, 8, 1), TripStatus.CANCELLED);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/vehicles/mine", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver.getUser())), ApiError.class);

        assertThat(response.getStatusCode())
                .as("sadece İPTAL EDİLMİŞ seferi olan DRIVER için de 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mine_asNonDriver_returns403() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/vehicles/mine", HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Sprint 3 kabul testinde bulunan hata: plaka boşluksuz saklanır, aramada boşluklu yazım da bulmalı. */
    @Test
    void list_searchWithSpacedPlate_findsNormalizedPlate() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        String suffix = String.valueOf(100 + new java.util.Random().nextInt(900));
        String letters = "Q" + UUID.randomUUID().toString().replaceAll("[^A-Za-z]", "").toUpperCase().substring(0, 2);
        restTemplate().exchange("/api/vehicles", HttpMethod.POST,
                new HttpEntity<>(new VehicleCreateRequest("41 " + letters + " " + suffix, 12, "Arama Testi"), authHeaderFor(admin)),
                VehicleResponse.class);

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> res = restTemplate().exchange(
                "/api/vehicles?q=41 " + letters + " " + suffix, HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)),
                com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("totalElements").asLong()).isEqualTo(1);
        assertThat(res.getBody().get("content").get(0).get("plate").asText()).isEqualTo("41" + letters + suffix);
    }
}
